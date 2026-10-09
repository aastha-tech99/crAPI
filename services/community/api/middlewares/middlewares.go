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

package middlewares

import (
	"errors"
	"net/http"
	"os"
	"strings"

	"crapi.proj/goservice/api/auth"
	"crapi.proj/goservice/api/responses"
	"github.com/jinzhu/gorm"
)

// allowedOrigins returns the set of permitted CORS origins read from the
// ALLOWED_ORIGINS environment variable (comma-separated). When the variable
// is unset or empty the list is empty and no Origin will be reflected.
func allowedOrigins() map[string]struct{} {
	raw := strings.TrimSpace(os.Getenv("ALLOWED_ORIGINS"))
	origins := make(map[string]struct{})
	if raw == "" {
		return origins
	}
	for _, o := range strings.Split(raw, ",") {
		o = strings.TrimSpace(o)
		if o != "" {
			origins[o] = struct{}{}
		}
	}
	return origins
}

// setAllowedOrigin checks the request Origin against the configured allowlist
// and, if it matches, sets the Access-Control-Allow-Origin header to that
// specific origin (never a wildcard).
func setAllowedOrigin(w http.ResponseWriter, r *http.Request) {
	origin := r.Header.Get("Origin")
	if origin == "" {
		return
	}
	allowed := allowedOrigins()
	if _, ok := allowed[origin]; ok {
		w.Header().Set("Access-Control-Allow-Origin", origin)
		w.Header().Set("Vary", "Origin")
	}
}

//SetMiddlewareJSON set content type and options
func SetMiddlewareJSON(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-type", "application/json")
		setAllowedOrigin(w, r)
		w.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE")
		w.Header().Set("Access-Control-Allow-Headers", "Accept, Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization")
		if r.Method == "OPTIONS" {
			return
		}
		next(w, r)
	}
}

//AccessControlMiddleware set content type of header
func AccessControlMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		setAllowedOrigin(w, r)
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS,PUT")
		w.Header().Set("Access-Control-Allow-Headers", "authorization,content-type")

		if r.Method == "OPTIONS" {
			return
		}

		next.ServeHTTP(w, r)
	})
}

//SetMiddlewareAuthentication checks Authentication token for each request.
func SetMiddlewareAuthentication(next http.HandlerFunc, db *gorm.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		_, err := auth.ExtractTokenID(r, db)
		if err != nil {
			responses.ERROR(w, http.StatusUnauthorized, errors.New("Unauthorized"))
			return
		}
		next(w, r)
	}
}
