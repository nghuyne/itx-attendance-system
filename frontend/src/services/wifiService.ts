import { Capacitor } from '@capacitor/core';

const INVALID_BSSIDS = new Set([
  '02:00:00:00:00:00', // Android obfuscated (missing Location permission)
  '00:00:00:00:00:00', // OEM disconnected/error state
  '(NONE)',            // Android not-connected string
]);

export const wifiService = {
  getBssid: async (): Promise<string | null> => {
    if (Capacitor.getPlatform() !== 'android') return null;
    try {
      const { CapacitorWifi } = await import('@capgo/capacitor-wifi');
      let timeoutId: ReturnType<typeof setTimeout>;
      const timeoutPromise = new Promise<never>((_, reject) => {
        timeoutId = setTimeout(() => reject(new Error('timeout')), 3000);
      });
      const info = await Promise.race([CapacitorWifi.getWifiInfo(), timeoutPromise]);
      clearTimeout(timeoutId!);
      const bssid = info.bssid?.trim().replace(/-/g, ':').toUpperCase();
      if (!bssid || INVALID_BSSIDS.has(bssid)) return null;
      return bssid;
    } catch {
      return null;
    }
  },
};
