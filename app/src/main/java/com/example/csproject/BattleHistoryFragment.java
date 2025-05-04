package com.example.csproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Fragment to display the user's battle history
 */
public class BattleHistoryFragment extends Fragment {

    private RecyclerView recyclerViewBattleHistory;
    private TextView textViewEmptyHistory;
    private ProgressBar progressBar;
    private BattleHistoryAdapter adapter;
    private FirebaseManager firebaseManager;
    private List<BattleHistory> battleHistories;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battle_history, container, false);

        // Initialize UI elements
        recyclerViewBattleHistory = view.findViewById(R.id.recyclerViewBattleHistory);
        textViewEmptyHistory = view.findViewById(R.id.textViewEmptyHistory);
        progressBar = view.findViewById(R.id.progressBarBattleHistory);

        // Set up back button
        ImageButton buttonBack = view.findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> {
            // Go back to the previous fragment or activity
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        // Initialize Firebase manager
        firebaseManager = FirebaseManager.getInstance();

        // Set up RecyclerView
        recyclerViewBattleHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        battleHistories = new ArrayList<>();
        adapter = new BattleHistoryAdapter(battleHistories, this::onBattleHistoryItemClick);
        recyclerViewBattleHistory.setAdapter(adapter);

        // Load battle histories
        loadBattleHistories();

        // Set solid background
        view.setBackgroundColor(getResources().getColor(android.R.color.white));

        return view;
    }

    private void loadBattleHistories() {
        // Check if user is signed in
        if (!firebaseManager.isUserSignedIn()) {
            textViewEmptyHistory.setText("Please sign in to view your battle history");
            textViewEmptyHistory.setVisibility(View.VISIBLE);
            recyclerViewBattleHistory.setVisibility(View.GONE);
            return;
        }

        // Show progress bar
        progressBar.setVisibility(View.VISIBLE);
        recyclerViewBattleHistory.setVisibility(View.GONE);
        textViewEmptyHistory.setVisibility(View.GONE);

        // Load battle histories from Firebase
        firebaseManager.getBattleHistory(new FirebaseManager.BattleHistoryCallback() {
            @Override
            public void onBattleHistoryLoaded(List<BattleHistory> histories) {
                progressBar.setVisibility(View.GONE);

                if (histories.isEmpty()) {
                    textViewEmptyHistory.setVisibility(View.VISIBLE);
                    recyclerViewBattleHistory.setVisibility(View.GONE);
                } else {
                    textViewEmptyHistory.setVisibility(View.GONE);
                    recyclerViewBattleHistory.setVisibility(View.VISIBLE);
                    
                    // Sort by timestamp (newest first)
                    Collections.sort(histories, (h1, h2) -> h2.getTimestamp().compareTo(h1.getTimestamp()));
                    
                    // Update adapter
                    battleHistories.clear();
                    battleHistories.addAll(histories);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Exception e) {
                progressBar.setVisibility(View.GONE);
                textViewEmptyHistory.setText("Failed to load battle history");
                textViewEmptyHistory.setVisibility(View.VISIBLE);
                recyclerViewBattleHistory.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onBattleHistoryItemClick(BattleHistory battleHistory) {
        // Instead of navigating to a separate activity, expand the item to show details
        if (adapter != null) {
            adapter.toggleItemExpansion(battleHistory);
        }
    }

    /**
     * Adapter for the battle history RecyclerView
     */
    private static class BattleHistoryAdapter extends RecyclerView.Adapter<BattleHistoryAdapter.ViewHolder> {

        private final List<BattleHistory> battleHistories;
        private final OnBattleHistoryItemClickListener listener;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault());
        private BattleHistory expandedBattleHistory = null;

        public interface OnBattleHistoryItemClickListener {
            void onItemClick(BattleHistory battleHistory);
        }

        public BattleHistoryAdapter(List<BattleHistory> battleHistories, OnBattleHistoryItemClickListener listener) {
            this.battleHistories = battleHistories;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_battle_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BattleHistory battleHistory = battleHistories.get(position);
            
            // Set opponent name
            holder.textViewOpponent.setText("vs. " + battleHistory.getOpponentName());
            
            // Set outcome with appropriate styling
            String outcome = battleHistory.getOutcome().toUpperCase();
            holder.textViewOutcome.setText(outcome);
            if (outcome.equals("WIN")) {
                holder.textViewOutcome.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
            } else if (outcome.equals("LOSS")) {
                holder.textViewOutcome.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
            } else {
                holder.textViewOutcome.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.darker_gray));
            }
            
            // Set date
            holder.textViewDate.setText("Date: " + dateFormat.format(battleHistory.getTimestamp()));
            
            // Set click listener for the view details button
            holder.buttonViewDetails.setOnClickListener(v -> {
                listener.onItemClick(battleHistory);
            });
            
            // Check if this item is expanded
            boolean isExpanded = battleHistory.equals(expandedBattleHistory);
            holder.detailsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            
            // Set details content if expanded
            if (isExpanded) {
                // Set player team
                holder.textViewPlayerTeam.setText(battleHistory.getPlayerTeam());
                
                // Set opponent team
                holder.textViewOpponentTeam.setText(battleHistory.getOpponentTeam());
                
                // Set battle log
                holder.textViewBattleLog.setText(battleHistory.getBattleLog());
            }
        }

        @Override
        public int getItemCount() {
            return battleHistories.size();
        }
        
        /**
         * Toggle expansion state of an item
         * @param battleHistory The battle history item to toggle
         */
        public void toggleItemExpansion(BattleHistory battleHistory) {
            // If this item is already expanded, collapse it
            if (battleHistory.equals(expandedBattleHistory)) {
                expandedBattleHistory = null;
            } else {
                // Otherwise expand this item and collapse any previously expanded item
                expandedBattleHistory = battleHistory;
            }
            notifyDataSetChanged();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textViewOpponent, textViewOutcome, textViewDate;
            TextView textViewPlayerTeam, textViewOpponentTeam, textViewBattleLog;
            View buttonViewDetails, detailsContainer;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textViewOpponent = itemView.findViewById(R.id.textViewOpponent);
                textViewOutcome = itemView.findViewById(R.id.textViewOutcome);
                textViewDate = itemView.findViewById(R.id.textViewDate);
                buttonViewDetails = itemView.findViewById(R.id.buttonViewDetails);
                
                // Detail view elements
                detailsContainer = itemView.findViewById(R.id.detailsContainer);
                textViewPlayerTeam = itemView.findViewById(R.id.textViewPlayerTeam);
                textViewOpponentTeam = itemView.findViewById(R.id.textViewOpponentTeam);
                textViewBattleLog = itemView.findViewById(R.id.textViewBattleLog);
            }
        }
    }
}
