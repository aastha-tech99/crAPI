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


class SecurityHeadersMiddleware:
    """
    Middleware that adds security headers to every response:
    - Content-Security-Policy
    - X-Content-Type-Options
    - Strict-Transport-Security
    - Permissions-Policy
    - Referrer-Policy
    """

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)

        if "Content-Security-Policy" not in response:
            response["Content-Security-Policy"] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"

        if "X-Content-Type-Options" not in response:
            response["X-Content-Type-Options"] = "nosniff"

        if "Strict-Transport-Security" not in response:
            response["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"

        if "Permissions-Policy" not in response:
            response["Permissions-Policy"] = "geolocation=(), camera=(), microphone=()"

        if "Referrer-Policy" not in response:
            response["Referrer-Policy"] = "strict-origin-when-cross-origin"

        return response
