/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package auth

import (
	"bytes"
	"crypto/rsa"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"os"
	"strings"

	"crapi.proj/goservice/api/models"
	"crapi.proj/goservice/api/utils"
	jwt "github.com/dgrijalva/jwt-go"
	"github.com/jinzhu/gorm"
)

type Token struct {
	Token string `json:"token"`
}

// jwksKey represents a single key in a JWKS response.
type jwksKey struct {
	Kty string `json:"kty"`
	N   string `json:"n"`
	E   string `json:"e"`
}

// jwksResponse represents the JWKS response from the identity service.
type jwksResponse struct {
	Keys []jwksKey `json:"keys"`
}

// fetchJWKSPublicKey fetches the JWKS from the identity service and returns the first RSA public key.
func fetchJWKSPublicKey() (*rsa.PublicKey, error) {
	identityService := os.Getenv("IDENTITY_SERVICE")
	scheme := "http"
	tlsEnabled, isTLS := os.LookupEnv("TLS_ENABLED")
	if isTLS && utils.IsTrue(tlsEnabled) {
		scheme = "https"
	}
	jwksURL := fmt.Sprintf("%s://%s/identity/api/auth/jwks.json", scheme, identityService)

	resp, err := http.Get(jwksURL)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch JWKS: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read JWKS response: %w", err)
	}

	var jwks jwksResponse
	if err := json.Unmarshal(body, &jwks); err != nil {
		return nil, fmt.Errorf("failed to parse JWKS: %w", err)
	}

	for _, key := range jwks.Keys {
		if key.Kty == "RSA" {
			nBytes, err := base64.RawURLEncoding.DecodeString(key.N)
			if err != nil {
				return nil, fmt.Errorf("failed to decode JWKS modulus: %w", err)
			}
			eBytes, err := base64.RawURLEncoding.DecodeString(key.E)
			if err != nil {
				return nil, fmt.Errorf("failed to decode JWKS exponent: %w", err)
			}
			n := new(big.Int).SetBytes(nBytes)
			e := new(big.Int).SetBytes(eBytes)
			return &rsa.PublicKey{N: n, E: int(e.Int64())}, nil
		}
	}

	return nil, errors.New("no RSA key found in JWKS")
}

// ExtractToken return token from Authorization Bearer
func ExtractToken(r *http.Request) string {
	keys := r.URL.Query()
	token := keys.Get("token")
	if token != "" {
		return token
	}
	bearerToken := r.Header.Get("Authorization")
	if len(strings.Split(bearerToken, " ")) == 2 {
		return strings.Split(bearerToken, " ")[1]
	}
	return ""
}

// ExtractTokenID Verify token either it's valid or not.
// If token is valid we extract username from token Claims.
// Then check that username in postgres database.
func ExtractTokenID(r *http.Request, db *gorm.DB) (uint32, error) {
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	tokenVerifyURL := fmt.Sprintf("http://%s/identity/api/auth/verify", os.Getenv("IDENTITY_SERVICE"))
	tls_enabled, is_tls := os.LookupEnv("TLS_ENABLED")
	if is_tls && utils.IsTrue(tls_enabled) {
		tokenVerifyURL = fmt.Sprintf("https://%s/identity/api/auth/verify", os.Getenv("IDENTITY_SERVICE"))
	}
	tokenString := ExtractToken(r)
	tokenJSON, err := json.Marshal(Token{Token: tokenString})
	if err != nil {
		log.Println(err)
		return 0, err
	}

	resp, err := http.Post(tokenVerifyURL, "application/json", bytes.NewBuffer(tokenJSON))
	if err != nil {
		log.Println(err)
		return 0, err
	}
	defer func() {
		if err := resp.Body.Close(); err != nil {
			log.Println("Error closing response body:", err)
		}
	}()

	tokenValid := resp.StatusCode == 200
	pubKey, err := fetchJWKSPublicKey()
	if err != nil {
		log.Println(err)
		return 0, err
	}
	token, err := jwt.ParseWithClaims(tokenString, jwt.MapClaims{}, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return pubKey, nil
	})
	if err != nil {
		log.Println(err)
		return 0, err
	}
	claims, ok := token.Claims.(jwt.MapClaims)

	if ok && tokenValid {
		name := claims["sub"]
		if name != nil {
			//converting name interface to string
			email := fmt.Sprintf("%v", name)
			// checking username in postgres databse.
			err := CheckTokenInDB(email, db)
			return 0, err
		}
		var uid uint32
		return uint32(uid), nil
	}

	return 0, errors.New("Unauthorized")
}

// CheckTokenInDB call FindUserByEmail and check that email in postgres database
func CheckTokenInDB(username string, db *gorm.DB) error {
	email := fmt.Sprintf("%v", username)
	//Calling user model for database query
	num, err := models.FindAuthorByEmail(email, db)

	if num != nil {
		return nil
	}
	if err != nil {
		return err
	}
	return err

}

// Pretty display the claims licely in the terminal
func Pretty(data interface{}) {
	b, err := json.MarshalIndent(data, "", " ")
	if err != nil {
		log.Println(err)
		return
	}

	log.Println(string(b))
}

//
