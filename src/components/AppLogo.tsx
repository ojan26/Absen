import React, { useState } from 'react';
import { School as SchoolIcon } from 'lucide-react';
import logoImg from '../logo.png';

interface AppLogoProps {
  className?: string;
  iconClassName?: string;
  variant?: 'light' | 'dark' | 'color';
}

export const AppLogo: React.FC<AppLogoProps> = ({ 
  className = "w-10 h-10", 
  iconClassName = "w-6 h-6",
  variant = 'color'
}) => {
  const [error, setError] = useState(false);

  // Fallback beautiful component if image fails to load or does not exist
  const FallbackIcon = () => {
    let bgStyle = "bg-sky-500/10 border border-sky-500/20 text-sky-400";
    if (variant === 'light') {
      bgStyle = "bg-white/10 border border-white/20 text-white";
    } else if (variant === 'dark') {
      bgStyle = "bg-slate-900/10 border border-slate-900/20 text-slate-800";
    } else if (variant === 'color') {
      bgStyle = "bg-gradient-to-tr from-indigo-500/10 via-sky-500/10 to-teal-500/10 border border-sky-500/20 text-sky-400";
    }
    
    return (
      <div className={`${className} ${bgStyle} rounded-2xl flex items-center justify-center transition-all duration-300 shadow-xs relative overflow-hidden group`}>
        {/* Glow effect */}
        <div className="absolute inset-0 bg-gradient-to-tr from-sky-400/20 via-indigo-500/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
        <SchoolIcon className={`${iconClassName} relative z-10 transition-transform duration-500 group-hover:scale-110`} />
      </div>
    );
  };

  if (error) {
    return <FallbackIcon />;
  }

  return (
    <div className={`${className} flex items-center justify-center overflow-hidden rounded-2xl relative bg-transparent transition-all duration-300`}>
      <img
        src={logoImg}
        alt="X-Degan App Logo"
        className="w-full h-full object-contain"
        referrerPolicy="no-referrer"
        onError={() => setError(true)}
      />
    </div>
  );
};
