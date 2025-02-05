package it.niedermann.nextcloud.deck.ui.card.attachments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.SharedElementCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;

import it.niedermann.nextcloud.deck.DeckLog;
import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.databinding.FragmentCardEditTabAttachmentsBinding;
import it.niedermann.nextcloud.deck.model.Attachment;
import it.niedermann.nextcloud.deck.model.enums.DBStatus;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.WrappedLiveData;
import it.niedermann.nextcloud.deck.ui.branding.BrandedFragment;
import it.niedermann.nextcloud.deck.ui.card.EditCardViewModel;
import it.niedermann.nextcloud.deck.util.FileUtils;

import static it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.LiveDataHelper.observeOnce;
import static it.niedermann.nextcloud.deck.ui.branding.BrandedActivity.applyBrandToFAB;
import static it.niedermann.nextcloud.deck.ui.card.attachments.CardAttachmentAdapter.VIEW_TYPE_DEFAULT;
import static it.niedermann.nextcloud.deck.ui.card.attachments.CardAttachmentAdapter.VIEW_TYPE_IMAGE;

public class CardAttachmentsFragment extends BrandedFragment implements AttachmentDeletedListener, AttachmentClickedListener {

    private FragmentCardEditTabAttachmentsBinding binding;
    private EditCardViewModel viewModel;

    private static final int REQUEST_CODE_ADD_ATTACHMENT = 1;
    private static final int REQUEST_PERMISSION = 2;

    private SyncManager syncManager;
    private CardAttachmentAdapter adapter;

    private int clickedItemPosition;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentCardEditTabAttachmentsBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(EditCardViewModel.class);

        syncManager = new SyncManager(requireContext());
        adapter = new CardAttachmentAdapter(
                requireContext(),
                getChildFragmentManager(),
                requireActivity().getMenuInflater(),
                this,
                viewModel.getAccount(),
                viewModel.getFullCard().getLocalId());
        binding.attachmentsList.setAdapter(adapter);

        updateEmptyContentView();

        final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int spanCount = (int) ((displayMetrics.widthPixels / displayMetrics.density) / getResources().getInteger(R.integer.max_dp_attachment_column));
        GridLayoutManager glm = new GridLayoutManager(getContext(), spanCount);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (adapter.getItemViewType(position)) {
                    case VIEW_TYPE_IMAGE:
                        return 1;
                    case VIEW_TYPE_DEFAULT:
                    default:
                        return spanCount;
                }
            }
        });
        binding.attachmentsList.setLayoutManager(glm);
        if (!viewModel.isCreateMode()) {
            // https://android-developers.googleblog.com/2018/02/continuous-shared-element-transitions.html?m=1
            // https://github.com/android/animation-samples/blob/master/GridToPager/app/src/main/java/com/google/samples/gridtopager/fragment/ImagePagerFragment.java
            setExitSharedElementCallback(new SharedElementCallback() {
                @Override
                public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                    AttachmentViewHolder selectedViewHolder = (AttachmentViewHolder) binding.attachmentsList
                            .findViewHolderForAdapterPosition(clickedItemPosition);
                    if (selectedViewHolder != null) {
                        sharedElements.put(names.get(0), selectedViewHolder.getPreview());
                    }
                }
            });
            adapter.setAttachments(viewModel.getFullCard().getAttachments(), viewModel.getFullCard().getId());
            updateEmptyContentView();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && viewModel.canEdit()) {
            binding.fab.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            REQUEST_PERMISSION);
                } else {
                    startFilePickerIntent();
                }
            });
            binding.fab.show();
            binding.attachmentsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 0)
                        binding.fab.hide();
                    else if (dy < 0)
                        binding.fab.show();
                }
            });
        } else {
            binding.fab.hide();
            binding.emptyContentView.hideDescription();
        }
        return binding.getRoot();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void startFilePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_ADD_ATTACHMENT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_ATTACHMENT && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                DeckLog.warn("data is null");
                return;
            }
            final Uri uri = data.getData();
            if (uri == null) {
                DeckLog.warn("data.getDate() returned null");
                return;
            }
            DeckLog.info("Uri: " + uri.toString());
            String path = FileUtils.getPath(getContext(), uri);
            if (path == null) {
                DeckLog.warn("path to file is null");
                return;
            }
            for (Attachment existingAttachment : viewModel.getFullCard().getAttachments()) {
                final String existingPath = existingAttachment.getLocalPath();
                if (existingPath != null && existingPath.equals(path)) {
                    Snackbar.make(binding.coordinatorLayout, R.string.attachment_already_exists, Snackbar.LENGTH_LONG).show();
                    return;
                }
            }
            final File uploadFile = new File(path);
            final Date now = new Date();
            final Attachment a = new Attachment();
            a.setMimetype(Attachment.getMimetypeForUri(getContext(), uri));
            a.setData(uploadFile.getName());
            a.setFilename(uploadFile.getName());
            a.setBasename(uploadFile.getName());
            a.setFilesize(uploadFile.length());
            a.setLocalPath(path);
            a.setLastModifiedLocal(now);
            a.setStatusEnum(DBStatus.LOCAL_EDITED);
            a.setCreatedAt(now);
            viewModel.getFullCard().getAttachments().add(a);
            adapter.addAttachment(a);
            if (!viewModel.isCreateMode()) {
                WrappedLiveData<Attachment> liveData = syncManager.addAttachmentToCard(viewModel.getAccount().getId(), viewModel.getFullCard().getLocalId(), Attachment.getMimetypeForUri(getContext(), uri), uploadFile);
                observeOnce(liveData, getViewLifecycleOwner(), (next) -> {
                    if (liveData.hasError()) {
                        viewModel.getFullCard().getAttachments().remove(a);
                        adapter.removeAttachment(a);
                        Snackbar.make(binding.coordinatorLayout, R.string.attachment_already_exists, Snackbar.LENGTH_LONG).show();
                    } else {
                        viewModel.getFullCard().getAttachments().remove(a);
                        adapter.removeAttachment(a);
                        viewModel.getFullCard().getAttachments().add(next);
                        adapter.addAttachment(next);
                    }
                });
            }
            updateEmptyContentView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                startFilePickerIntent();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public static Fragment newInstance() {
        return new CardAttachmentsFragment();
    }

    @Override
    public void onAttachmentDeleted(Attachment attachment) {
        adapter.removeAttachment(attachment);
        viewModel.getFullCard().getAttachments().remove(attachment);
        if (!viewModel.isCreateMode() && attachment.getLocalId() != null) {
            syncManager.deleteAttachmentOfCard(viewModel.getAccount().getId(), viewModel.getFullCard().getLocalId(), attachment.getLocalId());
        }
        updateEmptyContentView();
    }

    @Override
    public void onAttachmentClicked(int position) {
        this.clickedItemPosition = position;
    }


    private void updateEmptyContentView() {
        if (this.adapter == null || this.adapter.getItemCount() == 0) {
            this.binding.emptyContentView.setVisibility(View.VISIBLE);
            this.binding.attachmentsList.setVisibility(View.GONE);
        } else {
            this.binding.emptyContentView.setVisibility(View.GONE);
            this.binding.attachmentsList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        applyBrandToFAB(mainColor, textColor, binding.fab);
    }
}
