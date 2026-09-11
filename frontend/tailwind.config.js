/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./app/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: "#00355f",
          container: "#0f4c81",
          on: "#ffffff",
          "on-container": "#8ebdf9",
          fixed: "#d2e4ff",
          "fixed-dim": "#a0c9ff",
        },
        secondary: {
          DEFAULT: "#006c4a",
          container: "#82f5c1",
          on: "#ffffff",
          "on-container": "#00714e",
          fixed: "#85f8c4",
          "fixed-dim": "#68dba9",
        },
        tertiary: {
          DEFAULT: "#522900",
          container: "#733c00",
          on: "#ffffff",
          "on-container": "#ffa658",
          fixed: "#ffdcc3",
          "fixed-dim": "#ffb77d",
        },
        error: {
          DEFAULT: "#ba1a1a",
          container: "#ffdad6",
          on: "#ffffff",
          "on-container": "#93000a",
        },
        surface: {
          DEFAULT: "#f8f9ff",
          dim: "#cbdbf5",
          bright: "#f8f9ff",
          variant: "#d3e4fe",
          container: "#e5eeff",
          "container-low": "#eff4ff",
          "container-lowest": "#ffffff",
          "container-high": "#dce9ff",
          "container-highest": "#d3e4fe",
        },
        "on-surface": "#0b1c30",
        "on-surface-variant": "#42474f",
        "inverse-surface": "#213145",
        "inverse-on-surface": "#eaf1ff",
        outline: {
          DEFAULT: "#727780",
          variant: "#c2c7d1",
        },
      },
      fontFamily: {
        sans: ['"Be Vietnam Pro"', "sans-serif"],
        display: ['"Be Vietnam Pro"', "sans-serif"],
      },
      borderRadius: {
        sm: "0.25rem",
        DEFAULT: "0.5rem",
        md: "0.75rem",
        lg: "1rem",
        xl: "1.5rem",
        full: "9999px",
      },
      spacing: {
        gutter: "1rem",
        "gutter-mobile": "0.75rem",
        margin: "2rem",
        "margin-mobile": "1rem",
        "space-xs": "0.25rem",
        "space-sm": "0.5rem",
        "space-md": "1rem",
        "space-lg": "1.5rem",
        "space-xl": "2.5rem",
      },
    },
  },
  plugins: [
    // daisyUI component library
    require("daisyui"),
  ],
  daisyui: {
    themes: [
      {
        bdsTheme: {
          primary: "#00355f",
          "primary-content": "#ffffff",
          secondary: "#006c4a",
          "secondary-content": "#ffffff",
          accent: "#733c00",
          "accent-content": "#ffffff",
          neutral: "#213145",
          "neutral-content": "#eaf1ff",
          "base-100": "#ffffff",
          "base-200": "#f8f9ff",
          "base-300": "#e5eeff",
          info: "#0284c7",
          success: "#006c4a",
          warning: "#d97706",
          error: "#ba1a1a",
        },
      },
    ],
  },
};
