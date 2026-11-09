package com.example.dropcontroller.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.R;
import com.example.dropcontroller.logic.GraphModel;
import com.example.dropcontroller.views.GraphCanvasView;

/**
 * Graph tab: chart of the drop times and trigger times currently configured on the Drops and Settings tabs.
 * The model is rebuilt every time the tab becomes visible.
 */
public class GraphFragment extends Fragment {

    private GraphCanvasView graphView;

    public GraphFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        graphView = view.findViewById(R.id.graph_canvas);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        DropControllerApp app = (DropControllerApp) requireActivity().getApplication();
        graphView.setModel(GraphModel.build(app.getSettings(), app.getSequence()));
    }
}
