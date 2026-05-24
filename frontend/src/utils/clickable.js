/**
 * Spread on a non-button element to give it button-like click + keyboard
 * activation. Closes Sonar S6852 ("Visible, non-interactive elements
 * with click handlers must have at least one keyboard listener") on
 * every site that visually styles a div as a button — usually because
 * native <button> reset CSS is annoying to override.
 *
 *   <div {...clickable(() => openItem(item))}>...</div>
 *
 * Triggers the handler on Enter and Space, matching native button
 * semantics. Adds role="button" + tabIndex={0} so screen readers
 * announce it correctly and keyboard users can reach it.
 */
export function clickable(handler) {
  return {
    onClick: handler,
    onKeyDown: (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handler(e);
      }
    },
    role: 'button',
    tabIndex: 0,
  };
}
