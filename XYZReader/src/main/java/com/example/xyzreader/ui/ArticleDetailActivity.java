package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import com.example.xyzreader.R;
import com.example.xyzreader.Utils.TransitionCallback;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_POSITION = "extra_position";
    private Cursor mCursor;
    private long mStartId;


    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        setContentView(R.layout.activity_article_detail);

        // Postpone transition until the image of ViewPager's initial item is loaded
        supportPostponeEnterTransition();

        setupTransition();

        SelectedArticleSharedElementCallback sharedElementCallback = new SelectedArticleSharedElementCallback();
        setEnterSharedElementCallback(sharedElementCallback);

        getLoaderManager().initLoader(0, null, this);

        mPagerAdapter = new MyPagerAdapter(getFragmentManager(), sharedElementCallback);
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageMargin((int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
        mPager.setPageMarginDrawable(new ColorDrawable(0x22000000));

        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }

            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                }

            }
        });

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getData() != null) {
                mStartId = ItemsContract.Items.getItemId(getIntent().getData());
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAfterTransition();
    }

    @Override
    public void finishAfterTransition() {
        setResult();
        super.finishAfterTransition();
    }

    private void setResult() {
        int position = mPager.getCurrentItem();
        Intent data = new Intent();
        data.putExtra(EXTRA_POSITION, position);
        setResult(RESULT_OK, data);
    }



    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        mPagerAdapter.notifyDataSetChanged();

        // Select the start ID
        if (mStartId > 0) {
            mCursor.moveToFirst();
            // TODO: optimize
            while (!mCursor.isAfterLast()) {
                if (mCursor.getLong(ArticleLoader.Query._ID) == mStartId) {
                    final int position = mCursor.getPosition();
                    mPager.setCurrentItem(position, false);
                    break;
                }
                mCursor.moveToNext();
            }
            mStartId = 0;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        mPagerAdapter.notifyDataSetChanged();
    }

    public static int getPosition(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null && data.hasExtra(EXTRA_POSITION)) {
            return data.getIntExtra(EXTRA_POSITION, NO_POSITION);
        }
        return NO_POSITION;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupTransition() {
        TransitionInflater inflater = TransitionInflater.from(this);
        Transition sharedElementEnterTransition = inflater.inflateTransition(R.transition.image_shared_element_transition);
        sharedElementEnterTransition.addListener(new TransitionCallback() {
            @Override
            public void onTransitionEnd(Transition transition) {
                mPagerAdapter.setDontAnimate(false);
            }

            @Override
            public void onTransitionCancel(Transition transition) {
                mPagerAdapter.setDontAnimate(false);
            }
        });
        getWindow().setSharedElementEnterTransition(sharedElementEnterTransition);
        Slide slide = new Slide(Gravity.BOTTOM);
        slide.addTarget(R.id.article_body);
        slide.setInterpolator(new OvershootInterpolator());
        slide.setDuration(600);
        slide.setStartDelay(25);
        getWindow().setEnterTransition(slide);
    }

    private Fragment getCurrentFragment () {
        int fragmentIndex = mPager.getCurrentItem();
        return mPagerAdapter.getItem(fragmentIndex);
    }

    private class MyPagerAdapter extends FragmentStatePagerAdapter {
        private boolean mDontAnimate;
        private final SelectedArticleSharedElementCallback mSharedElementCallback;
        private Fragment mCurrentFragment;

        public MyPagerAdapter(FragmentManager fm, @NonNull SelectedArticleSharedElementCallback sharedElementCallback) {
            super(fm);
            mDontAnimate = true;
            mSharedElementCallback = sharedElementCallback;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            if (mCurrentFragment != object) {
                mCurrentFragment = (Fragment) object;
            }
            if (object instanceof ArticleDetailFragment) {
                int mCurrentPosition = position;
                mSharedElementCallback.setSharedElementViews(findViewById(R.id.photo), findViewById(R.id.detail_article_title), findViewById(R.id.detail_article_byline));

            }
       }

       public Fragment getCurrentFragment() {
            return mCurrentFragment;
       }

        @Override
        public Fragment getItem(int position) {
            mCursor.moveToPosition(position);
            return ArticleDetailFragment.newInstance(mCursor.getLong(ArticleLoader.Query._ID));
        }

        @Override
        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }

        public void setDontAnimate(boolean dontAnimate) {
            mDontAnimate = dontAnimate;
        }
    }


}
