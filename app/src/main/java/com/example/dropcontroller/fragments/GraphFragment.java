package com.example.dropcontroller.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.R;
import com.example.dropcontroller.config.PresetJson;
import com.example.dropcontroller.config.PresetRepository;
import com.example.dropcontroller.data.Preset;
import com.example.dropcontroller.logic.GraphModel;
import com.example.dropcontroller.services.SequenceRunner;
import com.example.dropcontroller.views.GraphCanvasView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Graph tab: chart of the drop times and trigger times currently configured on the Drops and Settings tabs,
 * and below it one card per saved preset (chart, Load, rename by long press, delete). Export and Import
 * write and read all presets as one JSON file chosen with the system file picker.
 * The models are rebuilt every time the tab becomes visible.
 */
public class GraphFragment extends Fragment {

    private static final String TAG = "GraphFragment";
    private static final String JSON_MIME_TYPE = "application/json";
    // Some file managers report a JSON file with another type, so the import picker accepts everything
    private static final String[] IMPORT_MIME_TYPES = {JSON_MIME_TYPE, "*/*"};
    // A presets file is a few KB; this limit only protects against picking a huge file by mistake
    private static final int MAX_IMPORT_CHARS = 5 * 1024 * 1024;

    private DropControllerApp app;
    private SequenceRunner sequenceRunner;
    private PresetRepository repository;

    private GraphCanvasView graphView;
    private LinearLayout presetList;
    private View emptyHint;
    private View exportButton;
    private final List<View> loadButtons = new ArrayList<>();
    private AlertDialog dialog;

    // The card whose name is being edited, if any
    private PresetCard editingCard;
    private OnBackPressedCallback cancelEditCallback;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService fileExecutor;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    private final SequenceRunner.Listener sequenceListener = new SequenceRunner.Listener() {
        @Override
        public void onActiveChanged(boolean active) {
            updateLoadButtons();
        }

        @Override
        public void onFinished(boolean timedOut) {
            // Nothing to do
        }
    };

    /** Create-document contract that makes the picker start in the Downloads folder. */
    private static final class CreateJsonDocument extends ActivityResultContracts.CreateDocument {
        CreateJsonDocument() {
            super(JSON_MIME_TYPE);
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String input) {
            return withDownloadsStart(super.createIntent(context, input));
        }
    }

    /** Open-document contract that makes the picker start in the Downloads folder. */
    private static final class OpenAnyDocument extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            return withDownloadsStart(super.createIntent(context, input));
        }
    }

    @NonNull
    private static Intent withDownloadsStart(@NonNull Intent intent) {
        // EXTRA_INITIAL_URI is honored on Android 8+; older versions open the picker's last folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"));
        }
        return intent;
    }

    /** Views and state of one preset card. */
    private static final class PresetCard {
        final Preset preset;
        final TextView nameView;
        final TextInputLayout renameLayout;
        final TextInputEditText renameInput;

        PresetCard(Preset preset, TextView nameView, TextInputLayout renameLayout, TextInputEditText renameInput) {
            this.preset = preset;
            this.nameView = nameView;
            this.renameLayout = renameLayout;
            this.renameInput = renameInput;
        }
    }

    public GraphFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileExecutor = Executors.newSingleThreadExecutor();
        // Registered in onCreate, so a result is delivered to the new instance after a rotation
        exportLauncher = registerForActivityResult(new CreateJsonDocument(), this::onExportTargetChosen);
        importLauncher = registerForActivityResult(new OpenAnyDocument(), this::onImportSourceChosen);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // A running file task finishes; it only touches the application context and the main handler
        fileExecutor.shutdown();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        app = (DropControllerApp) requireActivity().getApplication();
        sequenceRunner = app.getSequenceRunner();
        repository = app.getPresetRepository();

        graphView = view.findViewById(R.id.graph_canvas);
        presetList = view.findViewById(R.id.ll_presets);
        emptyHint = view.findViewById(R.id.tv_presets_empty);

        exportButton = view.findViewById(R.id.btn_presets_export);
        exportButton.setOnClickListener(v -> {
            cancelEdit();
            exportLauncher.launch(getString(R.string.presets_export_file_name));
        });
        view.findViewById(R.id.btn_presets_import).setOnClickListener(v -> {
            cancelEdit();
            importLauncher.launch(IMPORT_MIME_TYPES);
        });

        // Back cancels a rename in progress; the callback is enabled only while editing
        cancelEditCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                cancelEdit();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), cancelEditCallback);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        graphView.setModel(GraphModel.build(app.getSettings(), app.getSequence()));
        sequenceRunner.addListener(sequenceListener);
        rebuildPresetList();
    }

    @Override
    public void onPause() {
        super.onPause();
        sequenceRunner.removeListener(sequenceListener);
        // An edit that is still open is simply cancelled
        cancelEdit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        editingCard = null;
        loadButtons.clear();
    }

    // ----------------------------------------------------
    // Preset cards
    // ----------------------------------------------------

    private void rebuildPresetList() {
        editingCard = null;
        cancelEditCallback.setEnabled(false);
        presetList.removeAllViews();
        loadButtons.clear();

        List<Preset> presets = repository.getAll();
        emptyHint.setVisibility(presets.isEmpty() ? View.VISIBLE : View.GONE);
        exportButton.setEnabled(!presets.isEmpty());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Preset preset : presets) {
            presetList.addView(createCard(inflater, preset));
        }
        updateLoadButtons();
    }

    @NonNull
    private View createCard(@NonNull LayoutInflater inflater, @NonNull Preset preset) {
        View card = inflater.inflate(R.layout.item_preset_card, presetList, false);

        TextView nameView = card.findViewById(R.id.tv_preset_name);
        TextInputLayout renameLayout = card.findViewById(R.id.til_preset_rename);
        TextInputEditText renameInput = card.findViewById(R.id.et_preset_rename);
        PresetCard holder = new PresetCard(preset, nameView, renameLayout, renameInput);

        nameView.setText(preset.getName());
        nameView.setOnLongClickListener(v -> {
            startEdit(holder);
            return true;
        });
        renameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmEdit(holder);
                return true;
            }
            return false;
        });
        renameInput.setOnFocusChangeListener((v, hasFocus) -> {
            // Leaving the field confirms the new name; an invalid name cancels the edit
            if (!hasFocus && editingCard == holder) {
                if (!confirmEdit(holder)) {
                    cancelEdit();
                }
            }
        });

        GraphCanvasView chart = card.findViewById(R.id.graph_preset);
        chart.setModel(GraphModel.build(preset.getSettings(), preset.getSequence()));

        MaterialButton loadButton = card.findViewById(R.id.btn_preset_card_load);
        loadButton.setOnClickListener(v -> loadPreset(preset));
        loadButtons.add(loadButton);

        card.findViewById(R.id.btn_preset_delete).setOnClickListener(v -> confirmDelete(preset));
        return card;
    }

    private void updateLoadButtons() {
        boolean active = sequenceRunner.isActive();
        for (View button : loadButtons) {
            button.setEnabled(!active);
        }
    }

    private void loadPreset(@NonNull Preset preset) {
        if (!app.applyPreset(preset)) {
            Toast.makeText(requireContext(), R.string.preset_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        // The top chart shows the current setup, which has just changed
        graphView.setModel(GraphModel.build(app.getSettings(), app.getSequence()));
        Toast.makeText(requireContext(), getString(R.string.preset_loaded, preset.getName()), Toast.LENGTH_SHORT)
                .show();
    }

    // ----------------------------------------------------
    // Rename
    // ----------------------------------------------------

    private void startEdit(@NonNull PresetCard card) {
        if (editingCard == card) {
            return;
        }
        // Finish the previous edit first (cancelling it, like a tap elsewhere would)
        cancelEdit();
        editingCard = card;
        cancelEditCallback.setEnabled(true);

        card.renameLayout.setError(null);
        card.renameInput.setText(card.preset.getName());
        card.renameInput.setSelection(card.renameInput.length());
        card.nameView.setVisibility(View.GONE);
        card.renameLayout.setVisibility(View.VISIBLE);
        card.renameInput.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(card.renameInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Applies the edited name.
     *
     * @return false if the name was rejected (an error is shown in the field and the edit stays open)
     */
    private boolean confirmEdit(@NonNull PresetCard card) {
        String name = card.renameInput.getText() == null ? "" : card.renameInput.getText().toString().trim();
        if (name.equals(card.preset.getName())) {
            // Unchanged: same as cancelling
            cancelEdit();
            return true;
        }
        PresetRepository.NameCheck check = repository.rename(card.preset.getId(), name);
        switch (check) {
            case OK:
                hideKeyboard(card.renameInput);
                rebuildPresetList();
                return true;
            case EMPTY:
                card.renameLayout.setError(getString(R.string.preset_name_empty));
                return false;
            case TOO_LONG:
                card.renameLayout.setError(getString(R.string.preset_name_too_long, PresetRepository.MAX_NAME_LENGTH));
                return false;
            default:
                card.renameLayout.setError(getString(R.string.preset_name_duplicate));
                return false;
        }
    }

    private void cancelEdit() {
        PresetCard card = editingCard;
        if (card == null) {
            return;
        }
        editingCard = null;
        cancelEditCallback.setEnabled(false);
        hideKeyboard(card.renameInput);
        card.renameLayout.setError(null);
        card.renameLayout.setVisibility(View.GONE);
        card.nameView.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard(@NonNull View view) {
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // ----------------------------------------------------
    // Delete
    // ----------------------------------------------------

    private void confirmDelete(@NonNull Preset preset) {
        cancelEdit();
        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preset_delete_title)
                .setMessage(getString(R.string.preset_delete_message, preset.getName()))
                .setPositiveButton(R.string.preset_delete, (d, which) -> {
                    repository.delete(preset.getId());
                    rebuildPresetList();
                    Toast.makeText(requireContext(), getString(R.string.preset_deleted, preset.getName()),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    // ----------------------------------------------------
    // Export and import (JSON file chosen with the system picker)
    // ----------------------------------------------------

    private void onExportTargetChosen(@Nullable Uri uri) {
        if (uri == null) {
            // The picker was cancelled
            return;
        }
        // The text is built here on the main thread: the repository is used on the main thread only
        List<Preset> presets = repository.getAll();
        String text = PresetJson.write(presets);
        int count = presets.size();
        Context appContext = requireContext().getApplicationContext();
        fileExecutor.execute(() -> {
            boolean ok = writeText(appContext, uri, text);
            mainHandler.post(() -> showToast(appContext, ok
                    ? appContext.getString(R.string.presets_export_done, count)
                    : appContext.getString(R.string.presets_export_failed)));
        });
    }

    private void onImportSourceChosen(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        fileExecutor.execute(() -> {
            String text;
            try {
                text = readText(appContext, uri);
            } catch (IOException e) {
                Log.e(TAG, "Cannot read the presets file", e);
                mainHandler.post(() -> showToast(appContext, appContext.getString(R.string.presets_import_failed)));
                return;
            }
            if (text == null) {
                mainHandler.post(() -> showToast(appContext, appContext.getString(R.string.presets_import_too_large)));
                return;
            }
            List<Preset> parsed;
            try {
                parsed = PresetJson.read(text);
            } catch (PresetJson.FormatException e) {
                Log.w(TAG, "Rejected presets file", e);
                mainHandler.post(() -> showToast(appContext, appContext.getString(R.string.presets_import_invalid)));
                return;
            }
            mainHandler.post(() -> finishImport(appContext, parsed));
        });
    }

    private void finishImport(@NonNull Context appContext, @NonNull List<Preset> parsed) {
        PresetRepository.ImportResult result = repository.importAll(parsed);
        if (isAdded() && getView() != null) {
            rebuildPresetList();
        }
        showToast(appContext, appContext.getString(R.string.presets_import_done,
                result.getImported(), result.getRenamed(), result.getSkipped()));
    }

    private static void showToast(@NonNull Context context, @NonNull String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private static boolean writeText(@NonNull Context context, @NonNull Uri uri, @NonNull String text) {
        // "wt" truncates an existing file, so no old content stays after the new text
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                return false;
            }
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Cannot write the presets file", e);
            return false;
        }
    }

    /**
     * @return the text, or null if the file is larger than {@link #MAX_IMPORT_CHARS}
     */
    @Nullable
    private static String readText(@NonNull Context context, @NonNull Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("No input stream");
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
                if (builder.length() > MAX_IMPORT_CHARS) {
                    return null;
                }
            }
            return builder.toString();
        } catch (SecurityException e) {
            throw new IOException("Access to the file was denied", e);
        }
    }
}
