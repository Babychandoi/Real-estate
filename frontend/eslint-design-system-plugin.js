// Local ESLint rules for the design-system guard rails (DS-03, docs/design-system.md, review m6).
//
// `no-restricted-syntax`'s regex selectors can flag characters, but they cannot do arithmetic (is this rem/em/px
// value below 12px?), so the two length checks below are small custom rules instead. Kept in-repo rather than as
// a package since they only apply to this project's token scale.

/** px equivalent of a Tailwind arbitrary length ("10px", "0.625rem", "0.7em"), assuming the 16px root Tailwind
 * itself assumes; `null` when the unit isn't one of these three or the number doesn't parse. */
function toPixels(raw) {
  const match = /^(-?[\d.]+)(px|rem|em)$/.exec(raw.trim());
  if (!match) return null;
  const value = Number.parseFloat(match[1]);
  if (Number.isNaN(value)) return null;
  return match[2] === 'px' ? value : value * 16;
}

function checkTextClasses(raw, node, context) {
  const pattern = /text-\[([^\]]+)\]/g;
  let match;
  while ((match = pattern.exec(raw))) {
    const px = toPixels(match[1]);
    if (px != null && px > 0 && px < 12) {
      context.report({
        node,
        message: `"${match[0]}" is ${px}px: text below 12px is not allowed for information; use text-xs/text-label (12px) or larger.`,
      });
    }
  }
}

const noTinyText = {
  meta: {
    type: 'problem',
    docs: { description: 'Disallow Tailwind arbitrary text sizes (px/rem/em) below 12px.' },
    schema: [],
  },
  create(context) {
    return {
      Literal(node) {
        if (typeof node.value === 'string') checkTextClasses(node.value, node, context);
      },
      TemplateElement(node) {
        checkTextClasses(node.value.raw, node, context);
      },
    };
  },
};

const noTinyInlineFontSize = {
  meta: {
    type: 'problem',
    docs: { description: 'Disallow an inline style fontSize below 12px.' },
    schema: [],
  },
  create(context) {
    return {
      Property(node) {
        if (node.key.type !== 'Identifier' || node.key.name !== 'fontSize') return;
        const value = node.value;
        let px = null;
        if (value.type === 'Literal' && typeof value.value === 'number') {
          // React appends "px" to a bare number for fontSize, same as Tailwind's px unit.
          px = value.value;
        } else if (value.type === 'Literal' && typeof value.value === 'string') {
          px = toPixels(value.value);
        } else if (value.type === 'TemplateLiteral' && value.expressions.length === 0) {
          px = toPixels(value.quasis[0].value.raw);
        }
        if (px != null && px > 0 && px < 12) {
          context.report({
            node,
            message: `Inline fontSize of ${px}px is below 12px, not allowed for information; use 12px or larger.`,
          });
        }
      },
    };
  },
};

export default {
  rules: {
    'no-tiny-text': noTinyText,
    'no-tiny-inline-font-size': noTinyInlineFontSize,
  },
};
