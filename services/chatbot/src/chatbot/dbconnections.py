import os

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
MONGO_PASSWORD = os.environ.get("MONGO_DB_PASSWORD", "")  # must be set via environment
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

# Full connection URI must be provided via MONGO_DB_URL so credentials
# are never assembled in source code.
MONGO_CONNECTION_URI = os.environ.get(
    "MONGO_DB_URL",
    "mongodb://%s:%s/?directConnection=true" % (MONGO_HOST, MONGO_PORT),
)

MONGO_CONNECTION_URI_ATLAS = os.environ.get(
    "MONGO_DB_URL_ATLAS",
    "mongodb+srv://%s?retryWrites=true&w=majority" % (MONGO_HOST,),
)

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
POSTGRES_PASSWORD = os.environ.get("DB_PASSWORD", "")  # must be set via environment
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

POSTGRES_URI = os.environ.get(
    "POSTGRES_DB_URL",
    "postgresql://%s:%s/%s?sslmode=disable" % (POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB),
)

CHROMA_HOST = os.environ.get("CHROMA_HOST", "chromadb")
CHROMA_PORT = os.environ.get("CHROMA_PORT", "8000")
