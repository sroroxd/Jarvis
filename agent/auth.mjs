import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const ausfuehren = promisify(execFile);

/** Keine Modellanfrage, keine Kontodaten in der HTTP-Antwort. Claude Code prüft seinen eigenen Login. */
export async function pruefeAnmeldung() {
  try {
    const timeout = Math.max(100, Math.min(10000, Number(process.env.JARVIS_AGENT_AUTH_TIMEOUT_MS) || 8000));
    const { stdout } = await ausfuehren('claude', ['auth', 'status'], {
      timeout, maxBuffer: 64 * 1024, encoding: 'utf8',
    });
    const status = JSON.parse(stdout);
    if (status.loggedIn !== true) return { fehler: 'Claude Code ist nicht angemeldet. Bitte im Container „claude auth login“ aufrufen.' };
    if (!['claude.ai', 'oauth_token'].includes(status.authMethod)) {
      return { fehler: 'Claude Code verwendet kein Claude-Abo. Bitte API-Schlüssel-Konfiguration entfernen und mit dem Claude-Abo anmelden.' };
    }
    return { angemeldet: true };
  } catch {
    return { fehler: 'Claude-Code-Anmeldung konnte nicht bestätigt werden. Bitte im Container „claude auth status“ prüfen.' };
  }
}
