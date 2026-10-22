mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
cp -v certs/server.crt workshop/certs/
cp -v certs/server.crt identity/src/main/resources/certs/
cp -v certs/server.crt chatbot/certs/
cp -v certs/server.crt web/certs/
cp -v certs/server.crt community/certs/
# Copy private keys for local development only. Do NOT commit private keys to git.
# In production, provide keys via volume mount or the TLS_KEY environment variable.
cp -v certs/server.key workshop/certs/
cp -v certs/server.key identity/src/main/resources/certs/
cp -v certs/server.key chatbot/certs/
cp -v certs/server.key web/certs/
cp -v certs/server.key community/certs/
cd ./identity/src/main/resources/certs/
./keystore.sh
echo ""
echo "WARNING: Generated private keys are for local development only."
echo "Do NOT commit server.key files to version control."