/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f4f1fb",
          100: "#e7e0f7",
          200: "#c9b8ee",
          300: "#a98ee3",
          400: "#8a63d6",
          500: "#6b3fc7",
          600: "#5730a3",
          700: "#432480",
          800: "#2f195c",
          900: "#1c0f39",
          950: "#120a26",
        },
      },
    },
  },
  plugins: [],
};
