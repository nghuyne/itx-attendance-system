import React from 'react';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

// Must be a class component — getDerivedStateFromError/componentDidCatch require it
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('[ErrorBoundary] Uncaught error:', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return <div aria-live="assertive">{this.props.fallback}</div>;

      return (
        <div
          aria-live="assertive"
          className="flex flex-col items-center justify-center min-h-[200px] p-8"
        >
          <div className="bg-red-50 border border-red-300 rounded-lg p-6 max-w-md text-center">
            <p className="text-red-800 font-semibold mb-2">⚠️ Đã xảy ra lỗi không mong muốn</p>
            {this.state.error && (
              <p className="text-red-600 text-sm mb-4">{this.state.error.message}</p>
            )}
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 bg-danger text-white rounded-lg text-sm hover:opacity-90"
            >
              Tải lại trang
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
