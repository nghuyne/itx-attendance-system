import React, { useRef, useState, useCallback } from 'react';
import Webcam from 'react-webcam';

interface CameraViewfinderProps {
  onPhotoCapture: (base64: string) => void;
  onError: (error: string) => void;
}

async function compressImage(base64: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d');
      if (!ctx) { reject(new Error('Canvas not supported')); return; }
      ctx.drawImage(img, 0, 0);

      let quality = 0.9;
      let compressed = canvas.toDataURL('image/jpeg', quality);
      // ~500KB binary ≈ 682000 chars in base64 (33% overhead)
      while (compressed.length > 682_000 && quality > 0.1) {
        quality = Math.max(quality - 0.1, 0.1);
        compressed = canvas.toDataURL('image/jpeg', quality);
      }
      resolve(compressed);
    };
    img.onerror = () => reject(new Error('Image load error'));
    img.src = base64;
  });
}

export const CameraViewfinder: React.FC<CameraViewfinderProps> = ({
  onPhotoCapture,
  onError,
}) => {
  const webcamRef = useRef<Webcam>(null);
  const [isCaptured, setIsCaptured] = useState(false);
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);

  const capturePhoto = useCallback(async () => {
    if (!webcamRef.current || isProcessing || isCaptured) return;
    setIsProcessing(true);
    try {
      const imageSrc = webcamRef.current.getScreenshot();
      if (!imageSrc) { onError('Không thể chụp ảnh. Vui lòng thử lại.'); return; }
      const compressed = await compressImage(imageSrc);
      setCapturedImage(compressed);
      setIsCaptured(true);
      onPhotoCapture(compressed);
    } catch (_err) {
      onError('Lỗi khi chụp ảnh. Vui lòng thử lại.');
    } finally {
      setIsProcessing(false);
    }
  }, [isProcessing, isCaptured, onPhotoCapture, onError]);

  const retakePhoto = () => {
    setIsCaptured(false);
    setCapturedImage(null);
    onPhotoCapture('');
  };

  if (cameraError) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
        <p className="font-medium">Không thể truy cập camera</p>
        <p className="mt-1">{cameraError}</p>
        <p className="mt-2 text-xs">Vui lòng cấp quyền camera cho trình duyệt.</p>
      </div>
    );
  }

  if (isCaptured && capturedImage) {
    return (
      <div>
        <div className="rounded-lg overflow-hidden bg-slate-200">
          <img src={capturedImage} alt="Ảnh check-in" className="w-full object-cover" />
        </div>
        <button
          onClick={retakePhoto}
          className="mt-3 w-full px-4 py-2 min-h-[48px] bg-slate-200 text-slate-700 rounded-lg font-medium hover:bg-slate-300 transition-colors"
        >
          Chụp lại
        </button>
      </div>
    );
  }

  return (
    <div className="relative bg-slate-900 rounded-lg overflow-hidden" style={{ height: '40vh' }}>
      <Webcam
        ref={webcamRef}
        videoConstraints={{ facingMode: 'user', width: 1280, height: 720 }}
        screenshotFormat="image/jpeg"
        screenshotQuality={0.92}
        onUserMediaError={(_err) => {
          const errorMsg = 'Camera không khả dụng hoặc bị từ chối quyền truy cập.';
          setCameraError(errorMsg);
          onError(errorMsg);
        }}
        className="w-full h-full object-cover"
      />
      <button
        onClick={capturePhoto}
        disabled={isProcessing}
        aria-label="Chụp ảnh"
        className="absolute bottom-4 left-1/2 -translate-x-1/2 w-16 h-16 bg-white rounded-full shadow-lg flex items-center justify-center hover:bg-slate-100 active:scale-95 transition-transform disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {isProcessing ? (
          <div className="w-6 h-6 border-2 border-slate-400 border-t-transparent rounded-full animate-spin" />
        ) : (
          <div className="w-12 h-12 rounded-full border-4 border-slate-300" />
        )}
      </button>
    </div>
  );
};
