(function() {
  // Theme management
  const THEME_KEY = 'yaes-theme';
  const LIGHT_THEME = 'light';
  const DARK_THEME = 'dark';

  function getStoredTheme() {
    return localStorage.getItem(THEME_KEY);
  }

  function getPreferredTheme() {
    const stored = getStoredTheme();
    if (stored) return stored;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? DARK_THEME : LIGHT_THEME;
  }

  function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_KEY, theme);
    updateToggleButton(theme);
  }

  function updateToggleButton(theme) {
    const button = document.getElementById('theme-toggle');
    if (button) {
      button.textContent = theme === DARK_THEME ? '☀️' : '🌙';
      button.setAttribute('aria-label', `Switch to ${theme === DARK_THEME ? 'light' : 'dark'} mode`);
    }
  }

  function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === DARK_THEME ? LIGHT_THEME : DARK_THEME;
    setTheme(next);
  }

  // Initialize theme on load
  document.addEventListener('DOMContentLoaded', function() {
    setTheme(getPreferredTheme());

    // Add theme toggle button to body
    const toggleBtn = document.createElement('button');
    toggleBtn.id = 'theme-toggle';
    toggleBtn.className = 'theme-toggle-btn';
    toggleBtn.onclick = toggleTheme;
    document.body.appendChild(toggleBtn);
    updateToggleButton(getPreferredTheme());
  });

  // Listen for system theme changes
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function(e) {
    if (!getStoredTheme()) {
      setTheme(e.matches ? DARK_THEME : LIGHT_THEME);
    }
  });
})();
