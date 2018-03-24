/*
 * Copyright (c) 2018, Dan Hasting
 *
 * This file is part of WeatherRadar
 *
 * WeatherRadar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * WeatherRadar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with WeatherRadar.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.danhasting.radar;

import android.app.AlertDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import com.danhasting.radar.database.AppDatabase;
import com.danhasting.radar.database.Favorite;
import com.danhasting.radar.database.FavoriteViewModel;
import com.danhasting.radar.database.Source;
import com.danhasting.radar.fragments.NeedKeyFragment;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        NeedKeyFragment.OnOpenSettingsListener {

    DrawerLayout drawerLayout;
    SharedPreferences settings;

    Integer currentFavorite = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark));
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                NavigationView navigationView = findViewById(R.id.nav_view);
                for (int i = 0; i < navigationView.getMenu().size(); i++)
                    navigationView.getMenu().getItem(i).setChecked(false);
            }

            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        final NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        FavoriteViewModel viewModel = ViewModelProviders.of(this).get(FavoriteViewModel.class);
        viewModel.getFavorites().observe(this, new Observer<List<Favorite>>() {
            @Override
            public void onChanged(@Nullable List<Favorite> favorites) {
                populateFavorites(navigationView.getMenu(), favorites);
            }
        });


        Button settingsButton = navigationView.getHeaderView(0).findViewById(R.id.nav_settings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawerLayout.closeDrawers();
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(settingsIntent, 1);
            }
        });

        Button aboutButton = navigationView.getHeaderView(0).findViewById(R.id.nav_about);
        aboutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawerLayout.closeDrawers();
                if (!classNameEquals("AboutActivity")) {
                    Intent aboutIntent = new Intent(MainActivity.this, AboutActivity.class);
                    startActivity(aboutIntent);
                }
            }
        });

        if (classNameEquals("MainActivity"))
            startDefaultView();
        else if (settings.getBoolean("first_run", true)) {
            firstRunWelcome();

            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("first_run", false);
            editor.apply();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        drawerLayout.closeDrawers();

        final int id = menuItem.getItemId();

        if (!classNameEquals("SelectActivity")) {
            Intent selectIntent = new Intent(MainActivity.this, SelectActivity.class);

            if (id == R.id.nav_nws)
                selectIntent.putExtra("selection", Source.NWS);
            else if (id == R.id.nav_mosaic)
                selectIntent.putExtra("selection", Source.MOSAIC);
            else if (id == R.id.nav_wunderground)
                selectIntent.putExtra("selection", Source.WUNDERGROUND);

            if (selectIntent.hasExtra("selection")) {
                MainActivity.this.startActivity(selectIntent);
                return true;
            }
        }

        if (id != currentFavorite) {
            ExecutorService service =  Executors.newSingleThreadExecutor();
            service.submit(new Runnable() {
                @Override
                public void run() {
                    AppDatabase database = AppDatabase.getAppDatabase(getApplication());
                    Favorite favorite = database.favoriteDao().loadById(id);
                    if (favorite != null) startFavoriteView(favorite);
                }
            });
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationView navigationView = findViewById(R.id.nav_view);
        for (int i = 0; i < navigationView.getMenu().size(); i++)
            navigationView.getMenu().getItem(i).setChecked(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean classNameEquals(String name) {
        return this.getClass().getSimpleName().equals(name);
    }

    private void firstRunWelcome() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.welcome_header));
        builder.setMessage(getString(R.string.welcome_text));

        builder.setPositiveButton(R.string.welcome_more, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent aboutIntent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(aboutIntent);
            }
        });
        builder.setNegativeButton(R.string.welcome_dismiss, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void populateFavorites(Menu menu, List<Favorite> favorites) {
        SubMenu favMenu = menu.findItem(R.id.nav_favorites).getSubMenu();
        favMenu.clear();

        int i = 0;
        for (Favorite favorite : favorites) {
            favMenu.add(0, favorite.getUid(), i, favorite.getName());
            i++;
        }
    }

    private void startDefaultView() {
        if (settings.getBoolean("show_favorite", false)) {
            final int favoriteID = Integer.parseInt(settings.getString("default_favorite","0"));

            ExecutorService service =  Executors.newSingleThreadExecutor();
            service.submit(new Runnable() {
                @Override
                public void run() {
                    AppDatabase database = AppDatabase.getAppDatabase(getApplication());
                    final Favorite favorite = database.favoriteDao().loadById(favoriteID);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (favorite != null)
                                startFavoriteView(favorite);
                            else
                                startFormView();

                            if (classNameEquals("MainActivity"))
                                finish();
                        }
                    });
                }
            });
        } else {
            startFormView();

            if (classNameEquals("MainActivity"))
                finish();
        }
    }

    private void startFormView() {
        Intent selectIntent = new Intent(MainActivity.this, SelectActivity.class);

        if (settings.getBoolean("api_key_activated", false))
            selectIntent.putExtra("selection", Source.WUNDERGROUND);
        else
            selectIntent.putExtra("selection", Source.NWS);

        MainActivity.this.startActivity(selectIntent);
    }

    private void startFavoriteView(Favorite favorite) {
        Intent radarIntent = new Intent(MainActivity.this, RadarActivity.class);
        radarIntent.putExtra("source", Source.fromInt(favorite.getSource()));
        radarIntent.putExtra("location", favorite.getLocation());
        radarIntent.putExtra("type", favorite.getType());
        radarIntent.putExtra("loop", favorite.getLoop());
        radarIntent.putExtra("enhanced", favorite.getEnhanced());
        radarIntent.putExtra("distance", favorite.getDistance());
        radarIntent.putExtra("favorite", true);
        radarIntent.putExtra("name", favorite.getName());
        radarIntent.putExtra("favoriteID", favorite.getUid());
        MainActivity.this.startActivity(radarIntent);
    }

    public void openSettings() {
        Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivityForResult(settingsIntent, 1);
    }
}
