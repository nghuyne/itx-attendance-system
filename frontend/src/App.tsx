import { ToastContainer } from './components/common/ToastContainer';
import { OfflineBanner } from './components/common/OfflineBanner';

function App() {
  return (
    <>
      <OfflineBanner />
      <ToastContainer />
      <div className="min-h-screen bg-background flex items-center justify-center">
        <h1 className="text-2xl font-bold text-neutral">ITX Attendance</h1>
      </div>
    </>
  );
}

export default App;
