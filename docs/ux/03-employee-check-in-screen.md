# Employee Check-in Screen Specification

**Target:** Mobile (iOS/Android), <30 seconds completion time  
**Primary User:** Employee (Nhân viên)  
**Related PR Features:** FR-1 to FR-5, FR-11, FR-12

---

## 1. Screen Layout (Mobile-First)

### 1.1 Full Screen Wireframe

```
┌──────────────────────────────────────┐
│  [Clock Icon] 08:45  Giờ Làm: 9:00–17:30  │  ← Header
├──────────────────────────────────────┤
│ ⚠️  Wi-Fi hợp lệ ✓  | GPS: 50m          │  ← Status Banner
├──────────────────────────────────────┤
│ [Toggle] Chấm công ngoài văn phòng   │  ← ClientSiteMode (only if MAC invalid)
├──────────────────────────────────────┤
│                                       │
│  ┌─────────────────────────────────┐ │
│  │                                 │ │
│  │     🎥 Camera Viewfinder        │ │  ← CameraViewfinder (~40% height)
│  │  (Live video stream from        │ │
│  │   react-webcam)                 │ │
│  │                                 │ │
│  │        ●  [Shutter Button]      │ │
│  │          (large circle)         │ │
│  │                                 │ │
│  └─────────────────────────────────┘ │
│                                       │
├──────────────────────────────────────┤
│ ✓ Ảnh đã chụp                        │  ← PhotoPreview (if captured)
│ [Chụp lại]                           │
├──────────────────────────────────────┤
│   [✓ Xác nhận Check-in]  (enabled)   │  ← ActionButton (disabled until photo)
│          or                          │
│   [✗ Xác nhận Check-in]  (disabled)  │
└──────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Header Section

**Position:** Top, sticky  
**Background:** Gradient from primary color (emerald-600)  
**Text Color:** White

| Element | Type | Content | Tailwind |
|---|---|---|---|
| Clock icon | `<Icon>` | Clock symbol | `text-white` |
| Current time | `<Text>` | HH:MM (real-time update every 1s) | `text-2xl font-bold` |
| Shift name + times | `<Text>` | e.g., "Ca Sáng: 9:00–17:30" | `text-sm text-white/80` |

**Component:**
```tsx
interface HeaderProps {
  currentTime: Date;
  shift: Shift;
}

export const CheckInHeader: React.FC<HeaderProps> = ({ currentTime, shift }) => {
  const timeString = currentTime.toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className="bg-gradient-to-r from-emerald-600 to-emerald-700 text-white p-4 sticky top-0 z-10">
      <div className="flex items-center gap-2">
        <Clock className="w-6 h-6" />
        <span className="text-2xl font-bold">{timeString}</span>
      </div>
      <p className="text-sm text-white/80 mt-1">
        {shift.name}: {shift.start_time}–{shift.end_time}
      </p>
    </div>
  );
};
```

---

### 2.2 Status Banner (Context Awareness)

**Position:** Below header  
**Purpose:** Alert user to Network IP and GPS status (BEFORE camera interaction)  
**Visibility:** Always visible

| State | Appearance | Action |
|---|---|---|
| **IP Valid + GPS OK** | Green indicator | Proceed normally |
| **IP Invalid + GPS OK** | Red warning (outline) | Show Client Site Mode toggle |
| **GPS Unavailable** | Amber warning (outline) | Still allow check-in (office mode) |
| **No Internet** | Red error banner | Show "Offline" state, queue for sync |

**Component:**
```tsx
// Renamed MacWifiStatus → IpStatus (ADR-002: pivot MAC Wi-Fi → Public IP)
interface IpStatusProps {
  isConnected: boolean;
  isValid?: boolean;
  warningMessage?: string;
}

export const IpStatus: React.FC<IpStatusProps> = ({
  isConnected,
  isValid,
  warningMessage,
}) => {
  const bgColor = isValid ? 'bg-green-50' : 'bg-red-50';
  const borderColor = isValid ? 'border-green-300' : 'border-red-300';
  const textColor = isValid ? 'text-green-700' : 'text-red-700';

  return (
    <div className={`${bgColor} border ${borderColor} rounded-lg p-3 m-4`}>
      <div className={`${textColor} flex items-center gap-2`}>
        {isValid ? <CheckCircle className="w-5 h-5" /> : <AlertCircle className="w-5 h-5" />}
        <span className="font-medium">
          {isValid ? '✓ Mạng văn phòng hợp lệ' : '⚠️ Không nhận diện được mạng văn phòng'}
        </span>
      </div>
      {warningMessage && <p className={`${textColor} text-sm mt-1`}>{warningMessage}</p>}
    </div>
  );
};

interface GpsStatusProps {
  isAvailable: boolean;
  accuracy?: number;
  errorMessage?: string;
}

export const GpsStatus: React.FC<GpsStatusProps> = ({ isAvailable, accuracy, errorMessage }) => {
  const statusText = isAvailable
    ? `GPS: ~${Math.round(accuracy || 0)}m`
    : 'GPS không khả dụng';
  const color = isAvailable ? 'text-emerald-600' : 'text-amber-600';

  return <span className={`${color} text-sm font-medium`}>{statusText}</span>;
};
```

---

### 2.3 Client Site Mode Toggle

**Visibility:** Only show if:
- Public IP is **invalid** (not in company/personal whitelist — backend returns INVALID_IP)
- AND user is not already in client site mode

**Purpose:** Allow employee to opt into GPS-based check-in when outside office

**Behavior:**
- Toggle ON → Disable Public IP requirement, require GPS + Photo
- Toggle OFF → Re-enable Public IP requirement
- Cannot toggle after check-in submitted

**Component:**
```tsx
interface ClientSiteModeToggleProps {
  isEnabled: boolean;
  onToggle: (enabled: boolean) => void;
  disabled?: boolean;
}

export const ClientSiteModeToggle: React.FC<ClientSiteModeToggleProps> = ({
  isEnabled,
  onToggle,
  disabled = false,
}) => {
  return (
    <div className="border-2 border-amber-300 bg-amber-50 rounded-lg p-4 m-4">
      <label className="flex items-center gap-3 cursor-pointer">
        <input
          type="checkbox"
          checked={isEnabled}
          onChange={(e) => onToggle(e.target.checked)}
          disabled={disabled}
          className="w-5 h-5"
        />
        <span className="font-medium text-amber-900">Chấm công ngoài văn phòng</span>
      </label>
      <p className="text-xs text-amber-700 mt-2">
        GPS sẽ được sử dụng thay vì Wi-Fi.
      </p>
    </div>
  );
};
```

---

### 2.4 Camera Viewfinder

**Library:** `react-webcam`  
**Dimensions:** Full width, ~40% viewport height (mobile), smooth stream  
**Controls:**
- **Shutter Button:** Large circle (60px diameter), ripple on click
- **Flash toggle:** (if device supports)
- **Retake option:** After photo captured

**Constraints:**
- Only direct camera capture (no upload from gallery)
- Permission check: Request `camera` permission at app startup or screen load
- Timeout: If no camera access after 10s, allow fallback

**Component:**
```tsx
import Webcam from 'react-webcam';

interface CameraViewfinderProps {
  onPhotoCapture: (base64: string) => void;
  onError: (error: string) => void;
  width?: string;
  height?: string;
}

export const CameraViewfinder: React.FC<CameraViewfinderProps> = ({
  onPhotoCapture,
  onError,
  width = '100%',
  height = '40vh',
}) => {
  const webcamRef = useRef<Webcam>(null);
  const [isCaptured, setIsCaptured] = useState(false);
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const capturePhoto = useCallback(async () => {
    if (!webcamRef.current) return;

    setIsLoading(true);
    try {
      const imageSrc = webcamRef.current.getScreenshot();
      if (imageSrc) {
        // Compress image
        const compressed = await compressImage(imageSrc);
        setCapturedImage(imageSrc);
        setIsCaptured(true);
        onPhotoCapture(compressed);
      }
    } catch (err) {
      onError('Lỗi khi chụp ảnh');
    } finally {
      setIsLoading(false);
    }
  }, [onPhotoCapture, onError]);

  const retakePhoto = () => {
    setIsCaptured(false);
    setCapturedImage(null);
  };

  if (isCaptured && capturedImage) {
    return (
      <div className="w-full p-4">
        <div className="rounded-lg overflow-hidden bg-slate-200">
          <img src={capturedImage} alt="Captured" className="w-full" />
        </div>
        <button
          onClick={retakePhoto}
          className="mt-3 w-full px-4 py-2 bg-slate-300 text-slate-800 rounded-lg font-medium"
        >
          Chụp lại
        </button>
      </div>
    );
  }

  return (
    <div style={{ width, height }} className="relative bg-slate-900 rounded-lg overflow-hidden">
      <Webcam
        ref={webcamRef}
        videoConstraints={{ facingMode: 'user', width: 1280, height: 720 }}
        screenshotFormat="image/jpeg"
        className="w-full h-full"
      />

      {/* Shutter Button */}
      <button
        onClick={capturePhoto}
        disabled={isLoading}
        className="absolute bottom-4 left-1/2 transform -translate-x-1/2 w-16 h-16 bg-white rounded-full shadow-lg flex items-center justify-center hover:bg-slate-100 active:scale-95 transition-transform disabled:opacity-50"
      >
        <Circle className="w-8 h-8 text-slate-400" />
      </button>

      {/* Loading state */}
      {isLoading && (
        <div className="absolute inset-0 bg-black/30 flex items-center justify-center">
          <div className="animate-spin w-8 h-8 border-2 border-white border-t-transparent rounded-full" />
        </div>
      )}
    </div>
  );
};

// Helper: Compress image to ≤500KB
async function compressImage(base64: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      if (!ctx) return reject('Canvas context error');

      canvas.width = img.width;
      canvas.height = img.height;
      ctx.drawImage(img, 0, 0);

      let quality = 0.9;
      let compressed = canvas.toDataURL('image/jpeg', quality);
      while (compressed.length > 500 * 1024 && quality > 0.1) {
        quality -= 0.1;
        compressed = canvas.toDataURL('image/jpeg', quality);
      }
      resolve(compressed);
    };
    img.onerror = () => reject('Image load error');
    img.src = base64;
  });
}
```

---

### 2.5 Action Button Section

**Position:** Bottom, full width  
**Primary CTA:** Xác nhận Check-in / Xác nhận Check-out

**Button States:**

| State | Appearance | Behavior | Condition |
|---|---|---|---|
| **Enabled** | Solid emerald, white text, full opacity | Click to submit | MAC valid OR client site + GPS OK + Photo captured |
| **Disabled** | Gray out, reduced opacity, `cursor-not-allowed` | No action | Missing photo OR (MAC invalid AND not in client site mode) OR (client site ON but no GPS) |
| **Loading** | Spinner inside button, disabled | Waiting for server response | During API call |
| **Success** | Green check + "Thành công" message | Auto-navigate away | After successful check-in |
| **Error** | Red background, error message below | Show retry button | API failed |

**Component:**
```tsx
interface CheckInConfirmButtonProps {
  isEnabled: boolean;
  isLoading: boolean;
  error?: string;
  onSubmit: () => Promise<void>;
  onRetry?: () => void;
}

export const CheckInConfirmButton: React.FC<CheckInConfirmButtonProps> = ({
  isEnabled,
  isLoading,
  error,
  onSubmit,
  onRetry,
}) => {
  const handleClick = async () => {
    try {
      await onSubmit();
    } catch (err) {
      // Error handling done by parent
    }
  };

  return (
    <div className="p-4 space-y-2">
      <button
        onClick={handleClick}
        disabled={!isEnabled || isLoading}
        className={`
          w-full py-3 rounded-lg font-bold text-lg transition-all
          ${isEnabled && !isLoading
            ? 'bg-emerald-600 text-white hover:bg-emerald-700 active:scale-95'
            : 'bg-slate-300 text-slate-500 cursor-not-allowed'
          }
          ${isLoading ? 'opacity-75' : 'opacity-100'}
        `}
      >
        {isLoading && <span className="inline-block animate-spin mr-2">⊙</span>}
        {isLoading ? 'Đang xử lý...' : 'Xác nhận Check-in'}
      </button>

      {error && (
        <div className="bg-red-50 border border-red-300 rounded-lg p-3 flex justify-between items-center">
          <span className="text-red-700 text-sm">{error}</span>
          {onRetry && (
            <button
              onClick={onRetry}
              className="text-red-700 text-sm font-medium underline"
            >
              Thử lại
            </button>
          )}
        </div>
      )}
    </div>
  );
};
```

---

## 3. Complete Screen Component

```tsx
// components/employee/CheckInScreen.tsx

interface CheckInScreenProps {
  onSuccess: (record: AttendanceRecord) => void;
  onError: (error: string) => void;
}

export const CheckInScreen: React.FC<CheckInScreenProps> = ({ onSuccess, onError }) => {
  const [shiftData, setShiftData] = useState<Shift | null>(null);
  const [ipValid, setIpValid] = useState(false);  // IP resolved & validated server-side
  const [coords, setCoords] = useState<GeolocationCoordinates | null>(null);
  const [gpsError, setGpsError] = useState<string | null>(null);
  const [photoBase64, setPhotoBase64] = useState<string | null>(null);
  const [isClientSite, setIsClientSite] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const currentTime = new Date();

  // Fetch shift on mount
  useEffect(() => {
    const fetchShift = async () => {
      try {
        const shift = await attendanceService.getShiftForToday();
        setShiftData(shift);
      } catch (err) {
        onError('Lỗi khi tải thông tin ca làm việc');
      }
    };
    fetchShift();
  }, [onError]);

  // Validate Public IP on mount — resolved server-side via GET /api/attendance/validate-ip
  useEffect(() => {
    const checkIp = async () => {
      try {
        const result = await attendanceService.validateOfficeIp();  // backend reads X-Forwarded-For
        setIpValid(result.valid);
      } catch (err) {
        setIpValid(false);
      }
    };
    checkIp();
  }, []);

  // Fetch GPS on mount (but don't block)
  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setCoords(pos.coords),
        (err) => setGpsError(err.message),
        { timeout: 10000, enableHighAccuracy: true }
      );
    }
  }, []);

  // Check if form is complete
  const canSubmit = () => {
    if (!photoBase64) return false;
    if (!isClientSite && !ipValid) return false;
    if (isClientSite && !coords) return false;
    return true;
  };

  const handlePhotoCapture = (base64: string) => {
    setPhotoBase64(base64);
  };

  const handleClientSiteModeToggle = (enabled: boolean) => {
    setIsClientSite(enabled);
    if (enabled) {
      // Optionally, re-trigger GPS fetch
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (pos) => setCoords(pos.coords),
          (err) => setGpsError(err.message),
          { timeout: 10000, enableHighAccuracy: true }
        );
      }
    }
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      // Note: Public IP is resolved server-side — not sent from client
      const record = await attendanceService.checkIn({
        lat: coords?.latitude || null,
        lng: coords?.longitude || null,
        photo_base64: photoBase64!,
        is_client_site: isClientSite,
      });

      // Show success
      onSuccess(record);
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Lỗi không xác định';
      setSubmitError(errorMsg);
      onError(errorMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRetry = () => {
    setSubmitError(null);
    handleSubmit();
  };

  if (!shiftData) {
    return <div className="flex items-center justify-center h-screen">Đang tải...</div>;
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <CheckInHeader currentTime={currentTime} shift={shiftData} />

      <div className="flex-1 overflow-y-auto pb-4 space-y-3">
        {/* Status Banner — IP validated server-side */}
        <IpStatus
          isConnected={true}
          isValid={ipValid}
          warningMessage={!ipValid && !isClientSite ? 'Nhấn nút bên dưới để chấm công ngoài văn phòng' : undefined}
        />

        <div className="px-4 flex justify-between text-sm">
          <GpsStatus
            isAvailable={!!coords && !gpsError}
            accuracy={coords?.accuracy}
            errorMessage={gpsError}
          />
        </div>

        {/* Client Site Mode Toggle */}
        {!ipValid && (
          <ClientSiteModeToggle
            isEnabled={isClientSite}
            onToggle={handleClientSiteModeToggle}
            disabled={isSubmitting}
          />
        )}

        {/* Camera */}
        <div className="px-4">
          <CameraViewfinder
            onPhotoCapture={handlePhotoCapture}
            onError={onError}
            height="40vh"
          />
        </div>
      </div>

      {/* Action Button */}
      <div className="border-t border-slate-200 bg-white">
        <CheckInConfirmButton
          isEnabled={canSubmit()}
          isLoading={isSubmitting}
          error={submitError}
          onSubmit={handleSubmit}
          onRetry={handleRetry}
        />
      </div>
    </div>
  );
};
```

---

## 4. Edge Cases & Error Handling

| Edge Case | Behavior |
|---|---|
| **Camera permission denied** | Show banner "Cần cấp quyền camera để chấm công". Offer link to app settings. |
| **GPS timeout (10s)** | Allow check-in in office mode; set `gps_unavailable = true` flag. |
| **IP invalid + no client site toggle shown** | Show error "Cần bật Client Site Mode hoặc liên hệ Admin". |
| **S3 photo upload fails** | Show error "Lỗi tải ảnh. Vui lòng thử lại" + retry button. |
| **Internet disconnected** | Show offline banner. Queue request for sync when online. |
| **Network timeout (>30s)** | Show timeout error, offer retry. |
| **Duplicate check-in same minute** | Backend returns 409 Conflict. Show "Bạn đã chấm công rồi. Hãy chấm công lúc hết ca." |

---

## 5. Success State

After successful check-in:

```
┌─────────────────────────────────────┐
│     ✓ Check-in Thành công!          │
│                                      │
│     08:45 — Đúng giờ                │
│                                      │
│ Bản ghi được lưu với đủ 3 yếu tố:  │
│ ✓ Public IP                         │
│ ✓ GPS                               │
│ ✓ Ảnh                               │
│                                      │
│          [Quay về trang chủ]        │
└─────────────────────────────────────┘
```

**Action:** Navigate back to home/history, refresh roster.

---

## 6. Form Validation Rules (Zod Schema)

```typescript
// types/form.ts
import { z } from 'zod';

// Note: macAddress removed — Public IP is resolved server-side (ADR-002)
export const CheckInFormSchema = z.object({
  latitude: z.number().min(-90).max(90).optional(),
  longitude: z.number().min(-180).max(180).optional(),
  photoBase64: z
    .string()
    .min(1000, 'Ảnh chất lượng quá thấp')
    .refine(
      (photo) => photo.length <= 600000, // ~500KB
      'Ảnh quá lớn (max 500KB)'
    ),
  isClientSite: z.boolean().default(false),
});

export type CheckInFormData = z.infer<typeof CheckInFormSchema>;
```

---

## 7. Performance Considerations

- **Real-time clock:** Update every 1s using `setInterval`, not `setTimeout` (avoid drift)
- **Camera stream:** Use `Webcam` component's built-in optimization
- **Geolocation:** Request only once on mount; don't repeat unnecessarily
- **Photo compression:** Run in `Worker` thread if image >5MB to avoid blocking main thread
- **API calls:** Use `AbortController` to cancel pending requests on unmount
