import { clsx, type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

// Utilities generated from the design tokens (tailwind.config.js). tailwind-merge must know them, otherwise it
// would read `text-body` as a text colour and drop it next to `text-on-surface`.
const controls = ['control-sm', 'control-md', 'control-lg'];
const icons = ['icon-sm', 'icon-md', 'icon-lg'];
const typeScale = [
  'display',
  'display-mobile',
  'headline-lg',
  'headline-md',
  'headline-sm',
  'body',
  'body-sm',
  'label',
];

const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      'font-size': [{ text: typeScale }],
      leading: [{ leading: typeScale }],
      'min-h': [{ 'min-h': controls }],
      'min-w': [{ 'min-w': controls }],
      h: [{ h: [...controls, ...icons] }],
      w: [{ w: [...controls, ...icons] }],
      size: [{ size: [...controls, ...icons] }],
      rounded: [{ rounded: ['input', 'card', 'dialog', 'panel', 'pill'] }],
      shadow: [{ shadow: ['card', 'card-hover', 'elevated', 'cta'] }],
      z: [{ z: ['header', 'overlay', 'toast'] }],
      'outline-w': [{ outline: ['focus'] }],
      'outline-offset': [{ 'outline-offset': ['focus'] }],
      duration: [{ duration: ['fast', 'base'] }],
      ease: [{ ease: ['standard'] }],
    },
  },
});

/** Conditional class names with Tailwind conflicts resolved (the last utility of a group wins). */
export function cn(...values: ClassValue[]): string {
  return twMerge(clsx(values));
}
