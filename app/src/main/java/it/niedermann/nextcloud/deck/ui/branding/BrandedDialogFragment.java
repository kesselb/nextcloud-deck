package it.niedermann.nextcloud.deck.ui.branding;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import it.niedermann.nextcloud.deck.Application;

public abstract class BrandedDialogFragment extends DialogFragment implements Branded {

    @Override
    public void onStart() {
        super.onStart();

        @Nullable Context context = getContext();
        if (context != null) {
            if (Application.isBrandingEnabled(context)) {
                @ColorInt final int mainColor = Application.readBrandMainColor(context);
                @ColorInt final int textColor = Application.readBrandTextColor(context);
                applyBrand(mainColor, textColor);
            }
        }
    }
}
