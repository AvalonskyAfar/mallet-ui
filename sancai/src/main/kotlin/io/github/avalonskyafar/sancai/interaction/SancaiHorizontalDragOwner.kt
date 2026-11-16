package io.github.avalonskyafar.sancai.interaction

/**
 * Marks a view that claims horizontal drags which start on it: tracks, switches, steppers and
 * sliders.
 *
 * Gesture hosts such as page hosts, pagers, swipe-back layouts and swipe-to-reveal rows leave
 * a gesture that starts on such a view to the view, unless the gesture turns out to be
 * vertical. Implement it on your own horizontally draggable views to get the same treatment.
 */
public interface SancaiHorizontalDragOwner
