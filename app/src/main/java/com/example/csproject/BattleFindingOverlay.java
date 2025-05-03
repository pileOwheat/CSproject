package com.example.csproject;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Overlay that displays "Finding Opponent..." message while searching for a battle
 */
public class BattleFindingOverlay {
    private final Context context;
    private final ViewGroup container;
    private View overlayView;
    private TextView dotsTextView;
    private boolean isShowing = false;
    private final Handler animationHandler = new Handler(Looper.getMainLooper());
    private final Runnable animationRunnable;
    private int dotCount = 0;

    /**
     * Constructor for the overlay
     * @param context The context
     * @param container The container to add the overlay to
     */
    public BattleFindingOverlay(Context context, ViewGroup container) {
        this.context = context;
        this.container = container;

        // Initialize the animation runnable
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isShowing && dotsTextView != null) {
                    // Update the dots animation
                    dotCount = (dotCount + 1) % 4;
                    String dots = "";
                    for (int i = 0; i < dotCount; i++) {
                        dots += ".";
                    }
                    dotsTextView.setText(dots);
                    
                    // Schedule the next update
                    animationHandler.postDelayed(this, 500);
                }
            }
        };
    }

    /**
     * Show the overlay
     */
    public void show() {
        if (isShowing) return;
        
        // Inflate the overlay layout
        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.battle_finding_overlay, container, false);
        
        // Get the dots text view for animation
        dotsTextView = overlayView.findViewById(R.id.findingDots);
        
        // Add the overlay to the container
        container.addView(overlayView);
        
        // Start the animation
        isShowing = true;
        animationHandler.post(animationRunnable);
    }

    /**
     * Hide the overlay
     */
    public void hide() {
        if (!isShowing) return;
        
        // Stop the animation
        animationHandler.removeCallbacks(animationRunnable);
        
        // Remove the overlay from the container
        if (overlayView != null && overlayView.getParent() != null) {
            container.removeView(overlayView);
        }
        
        isShowing = false;
    }

    /**
     * Check if the overlay is currently showing
     * @return true if showing, false otherwise
     */
    public boolean isShowing() {
        return isShowing;
    }
}
