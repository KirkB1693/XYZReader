package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.Utils.TransitionCallback;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.recycler_view)
    RecyclerView mRecyclerView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);
        setupTransition();

        ButterKnife.bind(this);


        adapter = new Adapter(null);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);

        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    private void setupTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TransitionInflater inflater = TransitionInflater.from(this);
            Transition exitTransition = inflater.inflateTransition(R.transition.article_list_activity_exit);
            getWindow().setExitTransition(exitTransition);
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    public void onActivityReenter(int resultCode, Intent data) {
        final int position = ArticleDetailActivity.getPosition(resultCode, data);
        if (position != RecyclerView.NO_POSITION) {
            mRecyclerView.scrollToPosition(position);
        }

        final SelectedArticleSharedElementCallback sharedElementCallback = new SelectedArticleSharedElementCallback();
        setExitSharedElementCallback(sharedElementCallback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Listener to reset shared element exit transition callbacks.
            getWindow().getSharedElementExitTransition().addListener(new TransitionCallback() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    removeCallback();
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    removeCallback();
                }

                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                private void removeCallback() {
                        getWindow().getSharedElementExitTransition().removeListener(this);
                        setExitSharedElementCallback((SharedElementCallback) null);
                }
            });
        }

        //noinspection ConstantConditions
        supportPostponeEnterTransition();
        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);

                RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
                if (holder instanceof ViewHolder) {
                    ViewHolder mediaViewHolder = (ViewHolder) holder;
                    sharedElementCallback.setSharedElementViews(mediaViewHolder.thumbnailView, mediaViewHolder.titleView, mediaViewHolder.subtitleView);
                }

                supportStartPostponedEnterTransition();

                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Before Lollipop we don't have Activity.onActivityReenter() callback,
        // so we have to call GalleryFragment.onActivityReenter() here.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            onActivityReenter(resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        adapter.updateList(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ArticleListActivity.this, ArticleDetailActivity.class);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    Bundle bundle = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        getWindow().getExitTransition().excludeTarget(vh.cardView, true);
                        View transitionView = vh.thumbnailView;
                        String transitionID = transitionView.getTransitionName();
                        bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(ArticleListActivity.this, transitionView, transitionID).toBundle();
                    }
                    startActivity(intent, bundle);
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {

            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }

            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
            holder.imageLoaderHelper.getImageLoader().get(mCursor.getString(ArticleLoader.Query.THUMB_URL), new ImageLoader.ImageListener() {
                @Override
                public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                    Bitmap bitmap = imageContainer.getBitmap();
                    if (bitmap != null) {
                        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                            @Override
                            public void onGenerated(Palette palette) {
                                Palette.Swatch darkMuted = palette.getDarkMutedSwatch();
                                if (darkMuted != null) {
                                    holder.cardView.setCardBackgroundColor(darkMuted.getRgb());
                                } else {
                                    //dark muted colors don't exist, try to find dark vibrant swatches
                                    //or set a default color..
                                    Palette.Swatch darkVibrant = palette.getDarkVibrantSwatch();
                                    if (darkVibrant != null) {
                                        holder.cardView.setCardBackgroundColor(darkVibrant.getRgb());
                                    } else {
                                        holder.cardView.setCardBackgroundColor(getResources().getColor(R.color.cardview_dark_background));
                                    }
                                }
                            }
                        });

                    }
                }

                @Override
                public void onErrorResponse(VolleyError volleyError) {

                }


            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String transitionNameID = Integer.toString((int) mCursor.getLong(ArticleLoader.Query._ID));
                String transitionName = getResources().getString(R.string.transition_photo) + transitionNameID;
                holder.thumbnailView.setTransitionName(transitionName);
                String titleTransitionName = getString(R.string.title_transition_name) + transitionNameID;
                holder.titleView.setTransitionName(titleTransitionName);
                String bylineTransitionName = getString(R.string.byline_transition_name) + transitionNameID;
                holder.subtitleView.setTransitionName(bylineTransitionName);
            }

        }

        void updateList(Cursor newCursor) {

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new CursorCallback<Cursor>(this.mCursor, newCursor) {
                @Override
                public boolean areRowContentsTheSame(Cursor oldCursor, Cursor newCursor) {
                    boolean contentsTheSame = (oldCursor.getString(ArticleLoader.Query.TITLE).equals(newCursor.getString(ArticleLoader.Query.TITLE)));
                    Log.i("Adapter", "contents the same: " + contentsTheSame);
                    return contentsTheSame;
                }

                // as there is no unique id in the data, we simply regard the
                // article title as the unique id, as article titles are all different.
                @Override
                public boolean areCursorRowsTheSame(Cursor oldCursor, Cursor newCursor) {
                    boolean cursorRowsTheSame = (oldCursor.getString(ArticleLoader.Query.TITLE).equals(newCursor.getString(ArticleLoader.Query.TITLE)));
                    Log.i("Adapter", "cursor rows the same: " + cursorRowsTheSame);
                    return cursorRowsTheSame;
                }
            });
            diffResult.dispatchUpdatesTo(this);
            this.mCursor = newCursor;
        }

        @Override
        public int getItemCount() {
            if (mCursor == null) return 0;
            return mCursor.getCount();
        }
    }

    public abstract class CursorCallback<C extends Cursor> extends DiffUtil.Callback {
        private final C newCursor;
        private final C oldCursor;

        public CursorCallback(C newCursor, C oldCursor) {
            this.newCursor = newCursor;
            this.oldCursor = oldCursor;
        }

        @Override
        public int getOldListSize() {
            return oldCursor == null ? 0 : oldCursor.getCount();
        }

        @Override
        public int getNewListSize() {
            return newCursor == null? 0 : newCursor.getCount();
        }

        @Override
        public final boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldCursor.getColumnCount() == newCursor.getColumnCount() && moveCursorsToPosition(oldItemPosition, newItemPosition) && areCursorRowsTheSame(oldCursor, newCursor);
        }


        @Override
        public final boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldCursor.getColumnCount() == newCursor.getColumnCount() && moveCursorsToPosition(oldItemPosition, newItemPosition) && areRowContentsTheSame(oldCursor, newCursor);
        }

        @Nullable
        @Override
        public final Object getChangePayload(int oldItemPosition, int newItemPosition) {
            moveCursorsToPosition(oldItemPosition, newItemPosition);
            return getChangePayload(newCursor, oldCursor);
        }
        @Nullable
        public Object getChangePayload(C newCursor, C oldCursor) {
            return null;
        }

        private boolean moveCursorsToPosition(int oldItemPosition, int newItemPosition) {
            boolean newMoved = newCursor.moveToPosition(newItemPosition);
            boolean oldMoved = oldCursor.moveToPosition(oldItemPosition);
            return newMoved && oldMoved;
        }
        /** Cursors are already moved to positions where you should obtain data by row.
         *  Checks if contents at row are same
         *
         * @param oldCursor Old cursor object
         * @param newCursor New cursor object
         * @return See DiffUtil
         */
        public abstract boolean areRowContentsTheSame(Cursor oldCursor, Cursor newCursor);

        /** Cursors are already moved to positions where you should obtain data from row
         *  Checks if rows are the same, ideally, check by unique id
         * @param oldCursor Old cursor object
         * @param newCursor New cursor object
         * @return See DiffUtil
         */
        public abstract boolean areCursorRowsTheSame(Cursor oldCursor, Cursor newCursor);
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.article_card_view)
        CardView cardView;
        @BindView(R.id.thumbnail)
        DynamicHeightNetworkImageView thumbnailView;
        @BindView(R.id.article_title)
        TextView titleView;
        @BindView(R.id.article_subtitle)
        TextView subtitleView;
        ImageLoaderHelper imageLoaderHelper;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
            imageLoaderHelper = ImageLoaderHelper.getInstance(view.getContext());
        }
    }
}
