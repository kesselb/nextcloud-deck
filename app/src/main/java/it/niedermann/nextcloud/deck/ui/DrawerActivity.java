package it.niedermann.nextcloud.deck.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import com.nextcloud.android.sso.ui.UiExceptionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import it.niedermann.nextcloud.deck.DeckLog;
import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.api.IResponseCallback;
import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.model.ocs.Capabilities;
import it.niedermann.nextcloud.deck.model.ocs.Version;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.WrappedLiveData;
import it.niedermann.nextcloud.deck.ui.board.EditBoardDialogFragment;
import it.niedermann.nextcloud.deck.ui.exception.ExceptionHandler;
import it.niedermann.nextcloud.deck.util.ViewUtil;

public abstract class DrawerActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    protected static final int MENU_ID_ABOUT = -1;
    protected static final int MENU_ID_ADD_BOARD = -2;
    protected static final int MENU_ID_ADD_ACCOUNT = -2;
    protected static final int ACTIVITY_ABOUT = 1;
    protected static final long NO_ACCOUNTS = -1;
    protected static final long NO_BOARDS = -1;
    protected static final long NO_STACKS = -1;

    @BindView(R.id.coordinatorLayout)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.navigationView)
    NavigationView navigationView;
    @BindView(R.id.drawer_layout)
    DrawerLayout drawer;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    protected List<Account> accountsList = new ArrayList<>();
    protected Account account;
    protected boolean accountChooserActive = false;
    protected SyncManager syncManager;
    protected SharedPreferences sharedPreferences;
    private HeaderViewHolder headerViewHolder;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        AccountImporter.onActivityResult(requestCode, resultCode, data, this, (SingleSignOnAccount account) -> {
            final WrappedLiveData<Account> accountLiveData = this.syncManager.createAccount(new Account(account.name, account.userId, account.url));
            accountLiveData.observe(this, (Account createdAccount) -> {
                if (accountLiveData.hasError()) {
                    try {
                        accountLiveData.throwError();
                    } catch (SQLiteConstraintException ex) {
                        Snackbar.make(coordinatorLayout, getString(R.string.account_already_added), Snackbar.LENGTH_SHORT).show();
                    }
                } else {
                    syncManager.getServerVersion(new IResponseCallback<Capabilities>(createdAccount) {
                        @Override
                        public void onResponse(Capabilities response) {
                            if(response.getDeckVersion().compareTo(new Version(0, 6, 4)) < 0 ) {
                                new AlertDialog.Builder(DrawerActivity.this)
                                        .setTitle("You version might be too old")
                                        .setMessage(R.string.do_you_want_to_save_your_changes)
                                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> DeckLog.log("Version too old, OK pressed.")).show();
                            } else {
                                Snackbar.make(coordinatorLayout, getString(R.string.account_is_getting_imported), Snackbar.LENGTH_LONG).show();

                                // Remember last account
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                DeckLog.log("--- Write: shared_preference_last_account" + " | " + createdAccount.getId());
                                editor.putLong(getString(R.string.shared_preference_last_account), createdAccount.getId());
                                editor.apply();
                            }
                        }
                    });
                }
            });

            SingleAccountHelper.setCurrentAccount(getApplicationContext(), account.name);
        });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));
        setSupportActionBar(toolbar);

        View header = navigationView.getHeaderView(0);
        headerViewHolder = new HeaderViewHolder(header);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        syncManager = new SyncManager(this);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        syncManager.hasAccounts().observe(this, (Boolean hasAccounts) -> {
            if (hasAccounts != null && hasAccounts) {
                syncManager.readAccounts().observe(this, (List<Account> accounts) -> {
                    accountsList = accounts;
                    long lastAccountId = sharedPreferences.getLong(getString(R.string.shared_preference_last_account), NO_ACCOUNTS);
                    DeckLog.log("--- Read: shared_preference_last_account" + " | " + lastAccountId);

                    for (Account account : accounts) {
                        if (lastAccountId == account.getId() || lastAccountId == NO_ACCOUNTS) {
                            this.account = account;
                            SingleAccountHelper.setCurrentAccount(getApplicationContext(), this.account.getName());
                            syncManager = new SyncManager(this);
                            setHeaderView();
                            syncManager = new SyncManager(this);
                            ViewUtil.addAvatar(this, headerViewHolder.currentAccountAvatar, this.account.getUrl(), this.account.getUserName());
                            // TODO show spinner
                            syncManager.synchronize(new IResponseCallback<Boolean>(this.account) {
                                @Override
                                public void onResponse(Boolean response) {
                                    //nothing
                                }
                            });
                            accountSet(this.account);
                            break;
                        }
                    }
                });
            } else {
                showAccountPicker();
            }
        });

        navigationView.getHeaderView(0).findViewById(R.id.drawer_header_view).setOnClickListener(v -> {
            this.accountChooserActive = !this.accountChooserActive;
            if (accountChooserActive) {
                buildSidenavAccountChooser();
            } else {
                buildSidenavMenu();
            }
        });
    }

    private void showAccountPicker() {
        try {
            AccountImporter.pickNewAccount(this);
        } catch (NextcloudFilesAppNotInstalledException e) {
            UiExceptionManager.showDialogForException(this, e);
            Log.w("Deck", "=============================================================");
            Log.w("Deck", "Nextcloud app is not installed. Cannot choose account");
            e.printStackTrace();
        } catch (AndroidGetAccountsPermissionNotGranted e) {
            AccountImporter.requestAndroidAccountPermissionsAndPickAccount(this);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    protected abstract void accountSet(Account account);

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (accountChooserActive) {
            switch (item.getItemId()) {
                case MENU_ID_ADD_ACCOUNT:
                    showAccountPicker();
                    break;
                default:
                    this.account = accountsList.get(item.getItemId());
                    SingleAccountHelper.setCurrentAccount(getApplicationContext(), this.account.getName());
                    syncManager = new SyncManager(this);
                    setHeaderView();
                    accountChooserActive = false;
                    accountSet(this.account);

                    // Remember last account
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    DeckLog.log("--- Write: shared_preference_last_account" + " | " + this.account.getId());
                    editor.putLong(getString(R.string.shared_preference_last_account), this.account.getId());
                    editor.apply();
            }
        } else {
            switch (item.getItemId()) {
                case MENU_ID_ABOUT:
                    Intent aboutIntent = new Intent(getApplicationContext(), AboutActivity.class);
                    startActivityForResult(aboutIntent, ACTIVITY_ABOUT);
                    break;
                case MENU_ID_ADD_BOARD:
                    EditBoardDialogFragment.newInstance().show(getSupportFragmentManager(), getString(R.string.add_board));
                    break;
                default:
                    boardSelected(item.getItemId(), account);
            }
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected abstract void boardSelected(int itemId, Account account);

    protected void setHeaderView() {
        ViewUtil.addAvatar(this, navigationView.getHeaderView(0).findViewById(R.id.drawer_current_account), account.getUrl(), account.getUserName());
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.drawer_username_full)).setText(account.getName());
    }

    protected void setNoAccountHeaderView() {
        ViewUtil.addAvatar(this, navigationView.getHeaderView(0).findViewById(R.id.drawer_current_account), null, "");
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.drawer_username_full)).setText(getResources().getString(R.string.no_account));
    }

    private void buildSidenavAccountChooser() {
        Menu menu = navigationView.getMenu();
        menu.clear();
        if (accountsList == null || accountsList.size() == 0) {
            Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.app_name_short);
            setNoAccountHeaderView();
        } else {
            int index = 0;
            for (Account account : this.accountsList) {
                final int currentIndex = index;
                MenuItem m = menu.add(Menu.NONE, index++, Menu.NONE, account.getName()).setIcon(R.drawable.ic_person_grey600_24dp);
                AppCompatImageButton contextMenu = new AppCompatImageButton(this);
                contextMenu.setBackgroundDrawable(null);
                contextMenu.setImageDrawable(ViewUtil.getTintedImageView(this, R.drawable.ic_delete_black_24dp, R.color.grey600));
                contextMenu.setOnClickListener((v) -> {
                    if (currentIndex != 0) { // Select first account after deletion
                        this.account = accountsList.get(0);
                        SingleAccountHelper.setCurrentAccount(getApplicationContext(), this.account.getName());
                        syncManager = new SyncManager(this);
                        accountSet(this.account);
                        setHeaderView();
                        accountChooserActive = false;

                        // Remember last account
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        DeckLog.log("--- Write: shared_preference_last_account" + " | " + this.account.getId());
                        editor.putLong(getString(R.string.shared_preference_last_account), this.account.getId());
                        editor.apply();
                    } else if (accountsList.size() > 1) { // Select second account after deletion
                        this.account = accountsList.get(1);
                        SingleAccountHelper.setCurrentAccount(getApplicationContext(), this.account.getName());
                        syncManager = new SyncManager(this);
                        accountSet(this.account);
                        setHeaderView();
                        accountChooserActive = false;

                        // Remember last account
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        DeckLog.log("--- Write: shared_preference_last_account" + " | " + this.account.getId());
                        editor.putLong(getString(R.string.shared_preference_last_account), this.account.getId());
                        editor.apply();
                    } else {
                        accountsList.clear();
                    }

                    syncManager.deleteAccount(account.getId());
                    buildSidenavAccountChooser();
                    drawer.closeDrawer(GravityCompat.START);
                });
                m.setActionView(contextMenu);
            }
        }
        menu.add(Menu.NONE, MENU_ID_ADD_ACCOUNT, Menu.NONE, getString(R.string.add_account)).setIcon(R.drawable.ic_person_add_black_24dp);
    }

    abstract void buildSidenavMenu();

    protected static class HeaderViewHolder {
        @BindView(R.id.drawer_current_account)
        ImageView currentAccountAvatar;
        @BindView(R.id.drawer_account_middle)
        ImageView accountMiddleAvatar;
        @BindView(R.id.drawer_account_end)
        ImageView accountEndAvatar;

        @BindView(R.id.drawer_menu_toggle)
        LinearLayout drawerMenuToggle;
        @BindView(R.id.drawer_account_chooser_toggle)
        ImageView drawerMenuToggleIcon;

        HeaderViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }
}
