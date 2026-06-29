import { Capacitor } from '@capacitor/core';

export const wifiService = {
  getBssid: async (): Promise<string | null> => {
    if (Capacitor.getPlatform() !== 'android') return null;
    try {
      const { CapacitorWifi } = await import('@capgo/capacitor-wifi');
      const info = await CapacitorWifi.getWifiInfo();
      const bssid = info.bssid?.trim().toUpperCase();
      // Android obfuscates BSSID → '02:00:00:00:00:00' when Location permission is missing
      if (!bssid || bssid === '02:00:00:00:00:00') return null;
      return bssid;
    } catch {
      return null;
    }
  },
};
