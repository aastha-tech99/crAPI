#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from django.conf import settings


class ContentSecurityPolicyMiddleware:
    """Middleware that adds a Content-Security-Policy header to every response."""

    def __init__(self, get_response):
        self.get_response = get_response
        self.csp = getattr(
            settings,
            "CONTENT_SECURITY_POLICY",
            "default-src 'self'",
        )
        self.permissions_policy = getattr(
            settings,
            "PERMISSIONS_POLICY",
            "camera=(), microphone=(), geolocation=()",
        )

    def __call__(self, request):
        response = self.get_response(request)
        if "Content-Security-Policy" not in response:
            response["Content-Security-Policy"] = self.csp
        if "Permissions-Policy" not in response:
            response["Permissions-Policy"] = self.permissions_policy
        return response
