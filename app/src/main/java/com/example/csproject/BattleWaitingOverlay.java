package com.example.csproject;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * A waiting overlay that shows when a player has chosen a move or Pokémon
 * and is waiting for the opponent's action.
 */
public class BattleWaitingOverlay {
    private final View overlayView;
    private final TextView waitingActionText;
    private final TextView animatedDots;
    private final Button cancelButton;
    private final Handler handler;
    private final Runnable dotAnimationRunnable;
    private int dotCount = 0;
    private boolean isShowing = false;

    /**
     * Create a new battle waiting overlay
     * @param context The context
     * @param parent The parent view to attach the overlay to
     * @param onCancelListener Listener for when the cancel button is clicked
     */
    public BattleWaitingOverlay(Context context, ViewGroup parent, View.OnClickListener onCancelListener) {
        // Inflate the overlay layout
        overlayView = LayoutInflater.from(context).inflate(R.layout.battle_waiting_overlay, parent, false);
        
        // Remove the overlay if it was already added to prevent duplicates
        try {
            parent.removeView(overlayView);
        } catch (Exception ignored) {
            // View was not in parent, ignore
        }
        
        // Add the overlay as the last child to ensure it's on top of other views
        parent.addView(overlayView);
        
        // Make sure it's initially hidden
        overlayView.setVisibility(View.GONE);
        
        // Ensure the overlay is on top of other views
        overlayView.bringToFront();
        
        // Set z-index to ensure it's above other views
        overlayView.setZ(1000f);
        
        // Get views
        waitingActionText = overlayView.findViewById(R.id.waitingActionText);
        animatedDots = overlayView.findViewById(R.id.animatedDots);
        cancelButton = overlayView.findViewById(R.id.cancelButton);
        
        // Set cancel button listener
        cancelButton.setOnClickListener(onCancelListener);
        
        // Initialize dot animation
        handler = new Handler(Looper.getMainLooper());
        dotAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isShowing) {
                    dotCount = (dotCount + 1) % 4;
                    String dots = "";
                    for (int i = 0; i < dotCount; i++) {
                        dots += ".";
                    }
                    animatedDots.setText(dots);
                    handler.postDelayed(this, 500);
                }
            }
        };
    }
    
    /**
     * Show the overlay with a specific action text
     * @param actionText The text describing the action taken (e.g., "You chose: Thunderbolt")
     */
    public void show(String actionText) {
        waitingActionText.setText(actionText);
        
        // Make sure the overlay is visible and on top
        overlayView.setVisibility(View.VISIBLE);
        overlayView.bringToFront();
        
        // Set z-index to ensure it's above other views
        overlayView.setZ(1000f);
        
        // Request layout to ensure proper positioning
        overlayView.requestLayout();
        
        // Force parent to invalidate its layout
        ViewGroup parent = (ViewGroup) overlayView.getParent();
        if (parent != null) {
            parent.invalidate();
            parent.requestLayout();
        }
        
        // Log visibility state
        Log.d("BattleWaitingOverlay", "Showing overlay with text: " + actionText);
        Log.d("BattleWaitingOverlay", "Overlay visibility: " + (overlayView.getVisibility() == View.VISIBLE ? "VISIBLE" : "NOT VISIBLE"));
        
        isShowing = true;
        
        // Start dot animation
        dotCount = 0;
        handler.removeCallbacks(dotAnimationRunnable);
        handler.post(dotAnimationRunnable);
    }
    
    /**
     * Hide the overlay
     */
    public void hide() {
        // Stop animation
        handler.removeCallbacks(dotAnimationRunnable);
        
        // Hide the overlay
        overlayView.setVisibility(View.GONE);
        isShowing = false;
        
        // Log visibility state
        Log.d("BattleWaitingOverlay", "Hiding overlay");
    }
    
    /**
     * Check if the overlay is currently showing
     * @return true if showing, false otherwise
     */
    public boolean isShowing() {
        return isShowing;
    }
}
