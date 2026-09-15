import logging
import os

from .server import mcp as app

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

if __name__ == "__main__":
    logger.info("Starting MCP server...")
    mcp_server_port = int(os.environ.get("MCP_SERVER_PORT", 5500))
    mcp_server_host = os.environ.get("MCP_SERVER_HOST", "127.0.0.1")
    app.run(transport="streamable-http", host=mcp_server_host, port=mcp_server_port)
