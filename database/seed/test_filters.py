# Smoke-tests the frontend's list/filter pages against a seeded database.
# Usage: RTO_ADMIN_PASSWORD=... python test_filters.py   (optional: RTO_FRONTEND_URL, RTO_ADMIN_USER)
import os
import sys
import urllib.request
import urllib.parse
import http.cookiejar

BASE = os.environ.get("RTO_FRONTEND_URL", "http://localhost:8080")
USER = os.environ.get("RTO_ADMIN_USER", "admin")
PASSWORD = os.environ.get("RTO_ADMIN_PASSWORD")
if not PASSWORD:
    sys.exit("Set RTO_ADMIN_PASSWORD to the admin password of the seeded database.")

cookie_jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))

# 1. Login as admin
login_url = f"{BASE}/login"
data = urllib.parse.urlencode({"username": USER, "password": PASSWORD}).encode("utf-8")
req = urllib.request.Request(login_url, data=data, method="POST")
with opener.open(req) as resp:
    print(f"Login Response: {resp.status} (Final URL: {resp.url})")

# 2. Check various filter pages
endpoints = [
    ("/applications", "Citizen Applications"),
    ("/applications?status=REJECTED", "REJECTED"),
    ("/applications?status=APPROVED", "APPROVED"),
    ("/applications?status=DOCS_PENDING", "DOCS_PENDING"),
    ("/licences?status=SUSPENDED", "SUSPENDED"),
    ("/licences?status=EXPIRED", "EXPIRED"),
    ("/licences?status=REVOKED", "REVOKED"),
    ("/learner-licences?status=ACTIVE", "ACTIVE"),
    ("/learner-licences?status=CONVERTED", "CONVERTED"),
    ("/vehicles?status=BLACKLISTED", "BLACKLISTED"),
    ("/vehicles?status=SCRAPPED", "SCRAPPED"),
    ("/compliance?days=7", "Expiring Certificates"),
    ("/compliance?days=30", "Expiring Certificates"),
    ("/permits?status=ACTIVE", "ACTIVE"),
    ("/permits?status=SUSPENDED", "SUSPENDED"),
    ("/challans?status=DISPUTED", "DISPUTED"),
    ("/challans?status=PAID", "PAID"),
    ("/payments?status=SUCCESS", "SUCCESS"),
    ("/payments?status=FAILED", "FAILED"),
    ("/refunds?status=INITIATED", "INITIATED"),
    ("/refunds?status=COMPLETED", "COMPLETED"),
    ("/complaints?status=OPEN", "OPEN"),
    ("/complaints?status=IN_PROGRESS", "IN_PROGRESS"),
    ("/appeals?status=FILED", "FILED"),
    ("/appeals?status=UPHELD", "UPHELD"),
    ("/citizens", "Citizens"),
    ("/citizens?blacklisted=true", "Blacklisted only"),
    ("/citizens?blacklisted=false", "Not blacklisted"),
    ("/routes", "Transport Routes"),
    ("/driving-schools", "Driving Schools"),
    ("/driving-tests", "Driving Tests"),
    ("/driving-tests?result=PASS", "PASS"),
    ("/driving-tests?result=FAIL", "FAIL"),
    ("/admin/users", "User Accounts"),
    ("/audit-logs", "System Audit Trail"),
    ("/admin/roles", "Security Roles"),
    ("/admin/offices", "RTO Offices"),
    ("/admin/slots", "Appointment Slots"),
    ("/admin/reference", "Master Reference Data")
]

for path, expected_text in endpoints:
    url = f"{BASE}{path}"
    try:
        with opener.open(url) as resp:
            content = resp.read().decode("utf-8", errors="ignore")
            found = expected_text in content
            status_tag = "OK" if found else "NOT FOUND"
            print(f"[{status_tag}] {path} -> contains '{expected_text}'")
    except Exception as e:
        print(f"[ERR] {path} -> {e}")
