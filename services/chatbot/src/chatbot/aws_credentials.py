"""AWS credentials helper with STS assume role support."""

import logging
import os
import threading
import time
from typing import Optional

import boto3
from botocore.config import Config as BotocoreConfig

from .config import Config

logger = logging.getLogger(__name__)

# Clear empty token env vars that botocore might pick up and use for bearer token auth.
# Empty strings cause botocore to attempt token-based auth with an invalid token.
_TOKEN_ENV_VARS_TO_CLEAR = [
    "AWS_BEARER_TOKEN",
    "AWS_BEARER_TOKEN_BEDROCK",
    "AZURE_AD_TOKEN",
]
for _var in _TOKEN_ENV_VARS_TO_CLEAR:
    if _var in os.environ and not os.environ[_var]:
        logger.info("[SVC_ENV_INIT] Removing empty env var: %s", _var)
        del os.environ[_var]

# Cache for assumed role credentials
_credentials_cache = {
    "credentials": None,
    "expiration": 0,
    "lock": threading.Lock(),
}

# Refresh credentials 5 minutes before expiration
CREDENTIALS_REFRESH_BUFFER_SECONDS = 300


def _get_base_session():
    """Get a boto3 session with base credentials (from env vars or instance profile)."""
    region = os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION")
    _v1 = bool(os.getenv("AWS_ACCESS_KEY_ID"))
    _v2 = bool(os.getenv("AWS_SECRET_ACCESS_KEY"))
    _v3 = bool(os.getenv("AWS_SESSION_TOKEN"))
    logger.info(
        "[BASE_SESSION] Creating boto3 session - region: %s, v1: %s, "
        "v2: %s, v3: %s, will_use_instance_profile: %s",
        region,
        _v1,
        _v2,
        _v3,
        not (_v1 and _v2),
    )
    # Use None for empty strings so boto3 falls back to instance profile/IRSA
    session = boto3.Session(
        aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID") or None,
        aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY") or None,
        aws_session_token=os.getenv("AWS_SESSION_TOKEN") or None,
        region_name=region,
    )
    # Log the credential source boto3 is using
    creds = session.get_credentials()
    if creds:
        _src_method = creds.method if hasattr(creds, "method") else "unknown"
        _src_ok = bool(creds.access_key) if creds else False
        logger.info(
            "[BASE_SESSION] Session created - resolve_method: %s, v1: %s",
            _src_method,
            _src_ok,
        )
    else:
        logger.warning("[BASE_SESSION] Session created but NO config found!")
    return session


def _assume_role() -> dict:
    """Assume the configured IAM role and return temporary credentials."""
    role_arn = Config.AWS_ASSUME_ROLE_ARN
    external_id = Config.AWS_EXTERNAL_ID
    session_name = Config.AWS_ROLE_SESSION_NAME

    logger.info(
        "[ASSUME_ROLE] Starting assume role - role_arn: %s, session_name: %s, has_external_id: %s",
        role_arn,
        session_name,
        bool(external_id),
    )

    try:
        base_session = _get_base_session()
        logger.debug("Base session created successfully for assume role")
    except Exception as e:
        logger.error(
            "Failed to create base session for assume role - role_arn: %s, error: %s",
            role_arn,
            str(e),
        )
        raise

    try:
        sts_client = base_session.client("sts")
        logger.debug("STS client created successfully")
    except Exception as e:
        logger.error(
            "Failed to create STS client for assume role - role_arn: %s, error: %s",
            role_arn,
            str(e),
        )
        raise

    assume_role_kwargs = {
        "RoleArn": role_arn,
        "RoleSessionName": session_name,
        "DurationSeconds": 3600,  # 1 hour
    }

    if external_id:
        assume_role_kwargs["ExternalId"] = external_id
        logger.debug("External ID configured for assume role")

    logger.debug(
        "Calling STS assume_role with kwargs: %s",
        {k: v for k, v in assume_role_kwargs.items() if k != "ExternalId"},
    )

    try:
        logger.info("[ASSUME_ROLE] Calling sts:AssumeRole...")
        response = sts_client.assume_role(**assume_role_kwargs)
        credentials = response["Credentials"]

        _expiration = credentials["Expiration"]
        _assumed_role_id = response.get("AssumedRoleUser", {}).get("AssumedRoleId", "unknown")
        _v1_present = bool(credentials.get("AccessKeyId"))
        logger.info(
            "[ASSUME_ROLE] SUCCESS - ra: %s, session_name: %s, expires_at: %s, "
            "assumed_role_id: %s, v1: %s",
            role_arn,
            session_name,
            _expiration,
            _assumed_role_id,
            _v1_present,
        )

        return {
            "access_key": credentials["AccessKeyId"],
            "secret_key": credentials["SecretAccessKey"],
            "token": credentials["SessionToken"],
            "expiry_time": credentials["Expiration"].timestamp(),
        }
    except Exception as e:
        logger.error(
            "[ASSUME_ROLE] FAILED - role_arn: %s, session_name: %s, error_type: %s, error: %s",
            role_arn,
            session_name,
            type(e).__name__,
            str(e),
        )
        raise


def _get_cached_credentials() -> Optional[dict]:
    """Get cached credentials if they're still valid."""
    with _credentials_cache["lock"]:
        if _credentials_cache["credentials"] is None:
            logger.debug("No cached config available")
            return None

        # Check if credentials are about to expire
        time_until_expiry = _credentials_cache["expiration"] - time.time()
        if time_until_expiry <= CREDENTIALS_REFRESH_BUFFER_SECONDS:
            logger.info(
                "Cached config expiring soon - time_until_expiry: %.0f seconds, refresh_buffer: %d seconds",
                time_until_expiry,
                CREDENTIALS_REFRESH_BUFFER_SECONDS,
            )
            return None

        logger.debug(
            "Using cached config - time_until_expiry: %.0f seconds",
            time_until_expiry,
        )
        return _credentials_cache["credentials"]


def _set_cached_credentials(credentials: dict) -> None:
    """Cache the credentials."""
    with _credentials_cache["lock"]:
        _credentials_cache["credentials"] = credentials
        _credentials_cache["expiration"] = credentials["expiry_time"]
        _expiry = credentials["expiry_time"]
        logger.debug(
            "Cached new config - expires_at: %s", _expiry
        )


def get_aws_credentials() -> dict:
    """
    Get AWS credentials, using assume role if configured, or static credentials.

    Returns:
        dict with 'access_key', 'secret_key', 'token' (optional), and 'region'.

    Raises:
        RuntimeError: If no credentials are available.
    """
    region = os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION")
    _ra = bool(Config.AWS_ASSUME_ROLE_ARN)
    _p1 = bool(os.getenv("AWS_ACCESS_KEY_ID"))
    _p2 = bool(os.getenv("AWS_SECRET_ACCESS_KEY"))
    _bt = bool(Config.AWS_BEARER_TOKEN_BEDROCK)
    logger.info(
        "[SVC_INIT] get_config called - region: %s, ra: %s, "
        "p1: %s, p2: %s, bt: %s",
        region,
        _ra,
        _p1,
        _p2,
        _bt,
    )

    # If assume role is configured, use it
    if Config.AWS_ASSUME_ROLE_ARN:
        logger.info(
            "[SVC_INIT] Assume role path - ra: %s", Config.AWS_ASSUME_ROLE_ARN
        )
        # Try to use cached credentials
        cached = _get_cached_credentials()
        if cached:
            _cached_v1 = bool(cached.get("access_key"))
            logger.info(
                "[SVC_INIT] Using CACHED assume role config - v1: %s",
                _cached_v1,
            )
            return {
                "access_key": cached["access_key"],
                "secret_key": cached["secret_key"],
                "token": cached["token"],
                "region": region,
            }

        # Assume role and cache credentials
        logger.info("[SVC_INIT] No cached config, will call assume role now")
        credentials = _assume_role()
        _set_cached_credentials(credentials)
        _assumed_v1 = bool(credentials.get("access_key"))
        logger.info(
            "[SVC_INIT] Assume role succeeded - v1: %s",
            _assumed_v1,
        )
        return {
            "access_key": credentials["access_key"],
            "secret_key": credentials["secret_key"],
            "token": credentials["token"],
            "region": region,
        }

    # If static credentials are provided via environment variables
    access_key = os.getenv("AWS_ACCESS_KEY_ID")
    secret_key = os.getenv("AWS_SECRET_ACCESS_KEY")
    session_token = os.getenv("AWS_SESSION_TOKEN")

    if access_key and secret_key:
        _static_present = bool(access_key)
        _st_present = bool(session_token)
        logger.info(
            "[SVC_INIT] Using STATIC config from env - v1: %s, v2: %s",
            _static_present,
            _st_present,
        )
        result = {
            "access_key": access_key,
            "secret_key": secret_key,
            "region": region,
        }
        if session_token:
            result["token"] = session_token
        return result

    # No credentials available - raise error
    raise RuntimeError(
        "No AWS credentials available. Set AWS_ASSUME_ROLE_ARN for assume role, "
        "or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY for static credentials."
    )


def get_boto3_session() -> boto3.Session:
    """
    Get a boto3 session with explicit credentials.

    This handles assume role if configured, otherwise uses static credentials.
    """
    logger.debug("Creating boto3 session with explicit config")
    credentials = get_aws_credentials()

    return boto3.Session(
        aws_access_key_id=credentials["access_key"],
        aws_secret_access_key=credentials["secret_key"],
        aws_session_token=credentials.get("token"),
        region_name=credentials.get("region"),
    )


def get_bedrock_client():
    """
    Create a boto3 bedrock-runtime client with explicit credentials.

    This bypasses any token-based auth that botocore might pick up from env vars.
    """
    logger.info(
        "[BEDROCK_CLIENT] Creating bedrock-runtime client with explicit config"
    )
    credentials = get_aws_credentials()
    region = (
        credentials.get("region")
        or os.getenv("AWS_REGION")
        or os.getenv("AWS_DEFAULT_REGION")
    )

    _v1_present = bool(credentials.get("access_key"))
    _v2_present = bool(credentials.get("token"))
    logger.info(
        "[BEDROCK_CLIENT] Using config - v1: %s, v2: %s, region: %s",
        _v1_present,
        _v2_present,
        region,
    )

    # Create client with explicit credentials, bypassing any default chain or token discovery
    client = boto3.client(
        "bedrock-runtime",
        aws_access_key_id=credentials["access_key"],
        aws_secret_access_key=credentials["secret_key"],
        aws_session_token=credentials.get("token"),
        region_name=region,
        # Disable any retries on auth errors to fail fast
        config=BotocoreConfig(
            signature_version="v4",  # Force SigV4, not bearer token
            retries={"max_attempts": 3},
        ),
    )
    logger.info(
        "[BEDROCK_CLIENT] Client created successfully with explicit config"
    )
    return client


def get_bedrock_credentials_kwargs() -> dict:
    """
    Get kwargs to pass to ChatBedrock or BedrockEmbeddings for credentials.

    Always uses explicit credentials to bypass any token-based auth.

    Returns a dict that can be unpacked into the constructor.
    """
    logger.info("[BEDROCK_KWARGS] get_bedrock_config_kwargs called")

    try:
        credentials = get_aws_credentials()
    except Exception as e:
        logger.error(
            "[BEDROCK_KWARGS] Failed to get config: %s",
            type(e).__name__,
        )
        raise

    region = (
        credentials.get("region")
        or os.getenv("AWS_REGION")
        or os.getenv("AWS_DEFAULT_REGION")
    )

    # Pass explicit credentials to ChatBedrock/BedrockEmbeddings
    # This ensures we use SigV4 signing, not any token-based auth
    kwargs = {
        "region_name": region,
        "credentials_profile_name": None,  # Disable profile lookup
        "aws_access_key_id": credentials["access_key"],
        "aws_secret_access_key": credentials["secret_key"],
    }

    if credentials.get("token"):
        kwargs["aws_session_token"] = credentials["token"]

    _kw_v1 = bool(credentials.get("access_key"))
    _kw_v2 = bool(credentials.get("token"))
    logger.info(
        "[BEDROCK_KWARGS] Using explicit config - v1: %s, v2: %s, region: %s",
        _kw_v1,
        _kw_v2,
        region,
    )

    return kwargs
