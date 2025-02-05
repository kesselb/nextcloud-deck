package it.niedermann.nextcloud.deck.ui.board;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.databinding.DialogBoardCreateBinding;
import it.niedermann.nextcloud.deck.model.full.FullBoard;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.ui.MainViewModel;
import it.niedermann.nextcloud.deck.ui.branding.BrandedActivity;
import it.niedermann.nextcloud.deck.ui.branding.BrandedAlertDialogBuilder;
import it.niedermann.nextcloud.deck.ui.branding.BrandedDialogFragment;

public class EditBoardDialogFragment extends BrandedDialogFragment {

    private DialogBoardCreateBinding binding;

    private static final String KEY_BOARD_ID = "board_id";

    private EditBoardListener editBoardListener;

    private FullBoard fullBoard = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof EditBoardListener) {
            this.editBoardListener = (EditBoardListener) context;
        } else {
            throw new ClassCastException("Caller must implement " + EditBoardListener.class.getCanonicalName());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DialogBoardCreateBinding.inflate(requireActivity().getLayoutInflater());

        final Bundle args = getArguments();

        AlertDialog.Builder dialogBuilder = new BrandedAlertDialogBuilder(requireContext());

        if (args != null && args.containsKey(KEY_BOARD_ID)) {
            dialogBuilder.setTitle(R.string.edit_board);
            dialogBuilder.setPositiveButton(R.string.simple_save, (dialog, which) -> {
                this.fullBoard.board.setColor(binding.colorChooser.getSelectedColor().substring(1));
                this.fullBoard.board.setTitle(binding.input.getText().toString());
                editBoardListener.onUpdateBoard(fullBoard);
            });
            final MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
            new SyncManager(requireActivity()).getFullBoardById(viewModel.getCurrentAccount().getId(), args.getLong(KEY_BOARD_ID)).observe(EditBoardDialogFragment.this, (FullBoard fb) -> {
                if (fb.board != null) {
                    this.fullBoard = fb;
                    String title = this.fullBoard.getBoard().getTitle();
                    binding.input.setText(title);
                    binding.input.setSelection(title.length());
                    binding.colorChooser.selectColor("#" + fullBoard.getBoard().getColor());
                }
            });
        } else {
            dialogBuilder.setTitle(R.string.add_board);
            dialogBuilder.setPositiveButton(R.string.simple_add, (dialog, which) -> editBoardListener.onCreateBoard(binding.input.getText().toString(), binding.colorChooser.getSelectedColor()));
            binding.colorChooser.selectColor(String.format("#%06X", 0xFFFFFF & getResources().getColor(R.color.board_default_color)));
        }

        return dialogBuilder
                .setView(binding.getRoot())
                .setNeutralButton(android.R.string.cancel, null)
                .create();
    }

    public static DialogFragment newInstance(@Nullable Long boardId) {
        final DialogFragment dialog = new EditBoardDialogFragment();

        if (boardId != null) {
            Bundle args = new Bundle();
            args.putLong(KEY_BOARD_ID, boardId);
            dialog.setArguments(args);
        }

        return dialog;
    }

    public static DialogFragment newInstance() {
        return newInstance(null);
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        BrandedActivity.applyBrandToEditText(mainColor, textColor, binding.input);
    }
}