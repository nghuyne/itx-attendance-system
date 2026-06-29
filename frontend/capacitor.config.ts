import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.itx.attendance',
  appName: 'ITX Attendance',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  }
};

export default config;
