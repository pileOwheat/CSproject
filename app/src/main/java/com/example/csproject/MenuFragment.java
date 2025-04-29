package com.example.csproject;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;


public class MenuFragment extends Fragment {

    public MenuFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu, container, false);

        Button btnMain = view.findViewById(R.id.buttonBackToMain);
        Button btnSettings = view.findViewById(R.id.buttonSettings);
        Button btnClose = view.findViewById(R.id.buttonCloseMenu);

        btnMain.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            startActivity(intent);
            BattleActivity.isMenuOpen = false;
        });

        btnSettings.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Settings coming soon!", Toast.LENGTH_SHORT).show();
        });

        btnClose.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .remove(this)
                    .commit();
            requireActivity().getSupportFragmentManager().popBackStack();
                BattleActivity.isMenuOpen = false;
        });

        return view;
    }
}