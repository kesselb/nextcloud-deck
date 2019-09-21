package it.niedermann.nextcloud.deck.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.model.Card;
import it.niedermann.nextcloud.deck.model.Label;
import it.niedermann.nextcloud.deck.model.User;
import it.niedermann.nextcloud.deck.model.full.FullCard;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.ui.card.CardTabAdapter;
import it.niedermann.nextcloud.deck.ui.exception.ExceptionHandler;

import static it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.LiveDataHelper.observeOnce;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.BUNDLE_KEY_ACCOUNT_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.BUNDLE_KEY_BOARD_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.BUNDLE_KEY_LOCAL_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.BUNDLE_KEY_STACK_ID;
import static it.niedermann.nextcloud.deck.ui.card.CardAdapter.NO_LOCAL_ID;

public class EditActivity extends AppCompatActivity {

    SyncManager syncManager;

    @BindView(R.id.title)
    EditText title;

    @BindView(R.id.tab_layout)
    TabLayout tabLayout;

    @BindView(R.id.pager)
    ViewPager pager;

    private Unbinder unbinder;

    private FullCard fullCard;

    private long accountId;
    private long boardId;
    private long stackId;
    private long localId;

    private boolean createMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler(this));

        setContentView(R.layout.activity_edit);
        unbinder = ButterKnife.bind(this);

        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        title.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (fullCard != null) {
                    fullCard.getCard().setTitle(title.getText().toString());
                }
                String prefix = NO_LOCAL_ID.equals(localId) ? getString(R.string.add_card) : getString(R.string.edit);
                actionBar.setTitle(prefix + " " + title.getText());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            accountId = extras.getLong(BUNDLE_KEY_ACCOUNT_ID);
            boardId = extras.getLong(BUNDLE_KEY_BOARD_ID);
            stackId = extras.getLong(BUNDLE_KEY_STACK_ID);
            localId = extras.getLong(BUNDLE_KEY_LOCAL_ID);
            syncManager = new SyncManager(this);

            createMode = NO_LOCAL_ID.equals(localId);
            if (createMode) {
                actionBar.setTitle(getString(R.string.add_card));
                fullCard = new FullCard();
                fullCard.setLabels(new ArrayList<>());
                fullCard.setAssignedUsers(new ArrayList<>());
                Card card = new Card();
                card.setStackId(stackId);
                fullCard.setCard(card);
                setupViewPager();
            } else {
                observeOnce(syncManager.getCardByLocalId(accountId, localId), EditActivity.this, (next) -> {
                    fullCard = next;
                    title.setText(fullCard.getCard().getTitle());
                    setupViewPager();
                });
            }
        } else {
            throw new IllegalArgumentException("No localId argument");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.card_edit_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_card_save:
                saveAndFinish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveAndFinish() {
        //FIXME: nullpointer when no title entered! do not even save without title(?)
        if (fullCard.getCard().getTitle().isEmpty()) {
            if (!fullCard.getCard().getDescription().isEmpty()) {
                fullCard.getCard().setTitle(fullCard.getCard().getDescription().split("\n")[0]);
            } else {
                fullCard.getCard().setTitle("");
            }
        }
        if (createMode) {
            observeOnce(syncManager.createFullCard(accountId, boardId, stackId, fullCard), EditActivity.this, (card) -> finish());
        } else {
            observeOnce(syncManager.updateCard(fullCard.card), EditActivity.this, (card) -> finish());
        }
    }

    private void setupViewPager() {
        tabLayout.removeAllTabs();
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        CardTabAdapter adapter = new CardTabAdapter(getSupportFragmentManager(), getResources(), accountId, localId, boardId);
        pager.setAdapter(adapter);
        tabLayout.setupWithViewPager(pager);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
    }

    public void setDescription(String description) {
        this.fullCard.getCard().setDescription(description);
    }


    public void addUser(User user) {
        this.fullCard.getAssignedUsers().add(user);
    }


    public void addLabel(Label label) {
        this.fullCard.getLabels().add(label);
    }


    public void removeLabel(Label label) {
        this.fullCard.getLabels().remove(label);
    }

    public void setDueDate(Date dueDate) {
        this.fullCard.getCard().setDueDate(dueDate);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // close this activity as oppose to navigating up
        return true;
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.simple_save)
                .setMessage(R.string.do_you_want_to_save_your_changes)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> saveAndFinish())
                .setNegativeButton(R.string.simple_dismiss, (dialog, whichButton) -> super.onBackPressed()).show();
    }
}
