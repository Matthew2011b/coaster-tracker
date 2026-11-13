// The Lift Hill privacy policy, served at /privacy (Google Play links here). Keep it in step with what the app does:
// change it when the app starts sending anything new off the phone, and bump UPDATED.

const UPDATED = "November 12, 2026";
const CONTACT = "mrbsoftware.dev@gmail.com";

export const privacyPage = () => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Lift Hill Privacy Policy</title>
<meta name="description" content="How the Lift Hill app handles your data.">
<meta name="theme-color" content="#F0A04B">
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#141617;color:#E6E7E7;font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:28px 16px 48px}
.brand{display:flex;align-items:center;gap:10px;font-weight:600;font-size:20px;color:#F3F3F3;text-decoration:none}
h1{font-size:28px;line-height:1.2;margin:28px 0 4px;color:#F3F3F3}
h2{font-size:19px;margin:30px 0 6px;color:#F3F3F3}
p,li{color:#C4C7C9}ul{padding-left:22px}li{margin:4px 0}
.muted{color:#8E9395;font-size:14px}
a{color:#F0A04B}
.box{background:#1E2122;border:1px solid #33383A;border-radius:10px;padding:14px 16px;margin-top:18px}
.box p{margin:0}
</style></head><body><main>
<a class="brand" href="/"><svg width="30" height="30" viewBox="0 0 108 108" aria-hidden="true"><rect width="108" height="108" rx="24" fill="#25282A"/><g transform="translate(54 58) scale(.6) translate(-54 -58)"><path d="M8,88 L58,30 C64,23 70,24 74,32 L100,88" stroke="#F0A04B" stroke-width="5" fill="none" stroke-linecap="round"/></g></svg>Lift Hill</a>
<h1>Privacy Policy</h1>
<p class="muted">Last updated ${UPDATED}</p>

<div class="box"><p><strong>Short version:</strong> Lift Hill keeps your ride log on your phone. There are no accounts, ads, analytics or tracking.
Data only leaves your phone when you choose to: backing up to your own Google Drive, updating coaster data with your own
Coasterpedia key, sharing a link, or exporting a file.</p></div>

<h2>What stays on your phone</h2>
<p>The rides you log, your wishlist, any coasters you add or edit, and your settings and colors are stored only on your device.
The developer never receives them.</p>

<h2>Location</h2>
<p>If you allow it, Lift Hill uses your approximate location to sort parks and coasters by distance and to center the map.
This happens on your phone. Your location is not sent to the developer or anyone else, and it is not stored.
You can turn location off for Lift Hill in your phone's settings; the rest of the app keeps working.</p>

<h2>Google Drive backup (optional)</h2>
<p>If you sign in with Google on the Backup &amp; Data screen, Lift Hill asks for permission to use a private app folder in your
Google Drive. That permission only covers files Lift Hill creates there; the app cannot see or change anything else in your Drive.</p>
<ul>
<li>The backup file holds your rides, wishlist, coaster edits, settings and colors.</li>
<li>It is stored in your Google account, under <a href="https://policies.google.com/privacy">Google's Privacy Policy</a>. The developer has no access to it.</li>
<li>Your Google account's email address is saved on your phone so the app can show which account is signed in.</li>
<li>Signing out removes the account from the app and revokes its Drive permission.</li>
<li>To delete the backup, open Google Drive on the web, go to Settings, then Manage apps, find Lift Hill, and choose Delete hidden app data.</li>
</ul>

<h2>Coaster data updates (optional)</h2>
<p>If you enter your own Coasterpedia API key and tap Update data, the app sends requests with that key to Coasterpedia to download
coaster and park information. Nothing about your rides is sent. Coasterpedia's own terms and policies apply to those requests.</p>

<h2>Sharing</h2>
<p>When you finish a park, model or manufacturer and tap Share, the app makes a link containing an ID for that park, model or manufacturer
plus a few numbers (such as how many you rode, the date and your credit number). You choose where to send it. The link contains no name,
account or device information.</p>
<p>Links open pages on this site, which is hosted on Cloudflare. Like any web host, Cloudflare processes standard request information
(such as IP address and browser type) to deliver and protect the site; see <a href="https://www.cloudflare.com/privacypolicy/">Cloudflare's Privacy Policy</a>.
The developer does not add analytics or keep logs of who opens links.</p>

<h2>Spreadsheet export and import</h2>
<p>Export CSV saves your rides to a file in the place you pick. Import CSV reads a file you pick. Neither sends anything anywhere else.</p>

<h2>Children</h2>
<p>Lift Hill is not directed at children under 13 and does not knowingly collect personal information from anyone.</p>

<h2>Deleting your data</h2>
<p>Uninstalling Lift Hill deletes everything it stored on your phone. Delete a Google Drive backup as described above.</p>

<h2>Changes</h2>
<p>If this policy changes, the new version will be posted here with a new date.</p>

<h2>Contact</h2>
<p>Questions: <a href="mailto:${CONTACT}">${CONTACT}</a></p>
</main></body></html>`;
