package com.mapbox.tappergeochallenge.activity;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.tappergeochallenge.R;
import com.mapbox.tappergeochallenge.model.City;
import com.mapbox.tappergeochallenge.model.Player;
import com.mapbox.turf.TurfMeasurement;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds;
import static com.mapbox.tappergeochallenge.StringConstants.ONE_PLAYER_GAME;
import static com.mapbox.tappergeochallenge.StringConstants.PLAYER_ONE_NAME_KEY;
import static com.mapbox.tappergeochallenge.StringConstants.PLAYER_TWO_NAME_KEY;
import static com.mapbox.tappergeochallenge.StringConstants.TWO_PLAYER_GAME;
import static com.mapbox.tappergeochallenge.StringConstants.TYPE_OF_GAME_KEY;


public class GameActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMapClickListener,
    MapboxMap.OnInfoWindowClickListener, MapboxMap.OnMarkerClickListener {

  @BindView(R.id.mapview)
  MapView mapView;
  @BindView(R.id.check_answer_fab)
  FloatingActionButton checkAnswerFab;
  @BindView(R.id.location_to_guess_tv)
  TextView locationToGuess;
  @BindView(R.id.player_one_points)
  TextView playerOnePointsTextView;
  @BindView(R.id.player_two_points)
  TextView playerTwoPointsTextView;

  private static int CAMERA_BOUNDS_PADDING = 170;
  private static int EASE_CAMERA_SPEED_IN_MS = 1500;
  private MapboxMap mapboxMap;
  private Icon playerOneIcon;
  private Icon playerTwoIcon;
  private Icon bullsEyeIcon;
  private Player playerOne;
  private Player playerTwo;
  private Location randomCityLocation;
  public static List<Feature> listOfCities;
  public City randomTargetCity;
  private boolean isSinglePlayerGame;
  private boolean isTwoPlayerGame;
  private boolean playerOneHasGuessed;
  private boolean playerTwoHasGuessed;
  private Intent intent;
  private String TAG = "GameActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      loadCities();
    } catch (IOException exception) {
      Log.d(TAG, "onCreate: " + exception);
    }
    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    // TODO: Make sure that you paste your Mapbox token into this project's strings.xml file!
    Mapbox.getInstance(this, getString(R.string.mapbox_access_token));

    // This contains the MapView in XML and needs to be called after the access token is configured.
    setContentView(R.layout.game_activity_layout);

    // Bind views via the third-party Butterknife library
    ButterKnife.bind(this);

    intent = getIntent();

    // Determine whether a one or two person game was selected in the previous activity/screen
    setOneOrTwoPlayerGame(intent);


    // Miscellaneous setup
    checkAnswerFab.hide();
    playerOne = new Player();
    playerTwo = new Player();
    playerOneHasGuessed = false;
    playerTwoHasGuessed = false;

    // Set the visibility of parts of the card view that's on top of the Mapbox map
    if (isSinglePlayerGame) {
      playerOnePointsTextView.setVisibility(View.GONE);
      playerTwoPointsTextView.setVisibility(View.GONE);
    } else if (isTwoPlayerGame) {
      displayPlayerNamesAndPoints();
    }

    // Set up player and target city icons
    initializeIcons();

    // Retrieve the first city to guess
    getAndDisplayLocationToGuess();

    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(MapboxMap mapboxMap) {
    GameActivity.this.mapboxMap = mapboxMap;
    mapboxMap.setOnMarkerClickListener(this);
    mapboxMap.addOnMapClickListener(this);
    mapboxMap.setOnInfoWindowClickListener(this);
    adjustLogoOpacity();
    adjustAttributionOpacity();
  }

  @Override
  public void onMapClick(@NonNull LatLng point) {
    if (isSinglePlayerGame && !playerOneHasGuessed) {
      mapboxMap.clear();
      makeMarker(point, getString(R.string.single_player_game_marker_title),
          getString(R.string.click_here_to_confirm_selection),
          playerOneIcon);
    }

    if (isSinglePlayerGame && playerOneHasGuessed) {
      Snackbar.make(findViewById(android.R.id.content),
          R.string.player_one_already_chose_snackbar_message,
          Snackbar.LENGTH_SHORT).show();
    }

    if (isTwoPlayerGame && !playerOneHasGuessed && !playerTwoHasGuessed) {
      mapboxMap.clear();
      makeMarker(point, getResources().getString(R.string.player_one_selection, playerOne.getPlayerName()), getString(R.string.click_here_to_confirm_selection),
          playerOneIcon);
    }

    if (isTwoPlayerGame && playerOneHasGuessed && !playerTwoHasGuessed) {
      makeMarker(point, getResources().getString(R.string.player_two_selection, playerTwo.getPlayerName()),
          getString(R.string.click_here_to_confirm_selection),
          playerTwoIcon);

      for (Marker singleMarker : mapboxMap.getMarkers()) {
        if (singleMarker.getIcon().equals(playerTwoIcon) &&
            singleMarker.getPosition().getLatitude() != point.getLatitude()) {
          singleMarker.remove();
        }
      }
    }

    if (isTwoPlayerGame && playerOneHasGuessed && playerTwoHasGuessed) {
      Snackbar.make(findViewById(android.R.id.content),
          R.string.both_players_have_already_chose_snackbar_message,
          Snackbar.LENGTH_SHORT).show();
    }
  }

  @Override
  public boolean onInfoWindowClick(@NonNull Marker marker) {
    Icon iconOfSelectedMarker = marker.getIcon();
    if (isSinglePlayerGame && !playerOneHasGuessed) {
      playerOneHasGuessed = true;
      playerOne.setSelectedLatitude(marker.getPosition().getLatitude());
      playerOne.setSelectedLongitude(marker.getPosition().getLongitude());
      addTargetCityBullsEyeMarkerToMap(new LatLng(randomTargetCity.getCityLocation().getLatitude(),
          randomTargetCity.getCityLocation().getLongitude()), randomTargetCity.getCityName(), bullsEyeIcon);
      setCameraBoundsToSelectedAndTargetMarkers(marker);
      checkAnswerFab.setImageResource(R.drawable.ic_done_white);
      checkAnswerFab.show();
    } else if (isTwoPlayerGame && !playerOneHasGuessed && iconOfSelectedMarker == playerOneIcon) {
      playerOneHasGuessed = true;
      playerOne.setSelectedLatitude(marker.getPosition().getLatitude());
      playerOne.setSelectedLongitude(marker.getPosition().getLongitude());
    } else if (isTwoPlayerGame && playerOneHasGuessed && iconOfSelectedMarker == playerTwoIcon) {
      playerTwoHasGuessed = true;
      playerTwo.setSelectedLatitude(marker.getPosition().getLatitude());
      playerTwo.setSelectedLongitude(marker.getPosition().getLongitude());
      addTargetCityBullsEyeMarkerToMap(new LatLng(randomTargetCity.getCityLocation().getLatitude(),
          randomTargetCity.getCityLocation().getLongitude()), randomTargetCity.getCityName(), bullsEyeIcon);
      setCameraBoundsToSelectedAndTargetMarkers(marker);
      checkAnswerFab.setImageResource(R.drawable.ic_done_all_white);
      checkAnswerFab.show();
    }
    return false;
  }

  @Override
  public boolean onMarkerClick(@NonNull Marker marker) {
    if (marker.getIcon() == bullsEyeIcon) {
      CameraPosition position = new CameraPosition.Builder()
          .target(new LatLng(marker.getPosition().getLatitude(),
              marker.getPosition().getLongitude()))
          .zoom(5)
          .build();
      mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2500);
    }
    return false;
  }

  private void adjustLogoOpacity() {
    int MAPBOX_LOGO_OPACITY = 70;
    ImageView logo = mapView.findViewById(R.id.logoView);
    logo.setAlpha(MAPBOX_LOGO_OPACITY);
  }

  private void adjustAttributionOpacity() {
    int ATTRIBUTION_OPACITY = 70;
    ImageView attribution = mapView.findViewById(R.id.attributionView);
    attribution.setAlpha(ATTRIBUTION_OPACITY);
  }

  private void displayPlayerNamesAndPoints() {
    playerOne.setPlayerName(intent.getStringExtra(PLAYER_ONE_NAME_KEY));
    playerTwo.setPlayerName(intent.getStringExtra(PLAYER_TWO_NAME_KEY));
    displayPlayersPoints();
  }

  private void setCameraBoundsToSelectedAndTargetMarkers(Marker marker) {
    LatLngBounds latLngBounds = null;
    if (marker != null) {
      if (isSinglePlayerGame) {
        latLngBounds = new LatLngBounds.Builder()
            .include(marker.getPosition())
            .include(new LatLng(randomCityLocation.getLatitude(), randomCityLocation.getLongitude()))
            .build();
      }
      if (isTwoPlayerGame) {
        latLngBounds = new LatLngBounds.Builder()
            .include(marker.getPosition())
            .include(new LatLng(randomCityLocation.getLatitude(), randomCityLocation.getLongitude()))
            .include(new LatLng(playerOne.getSelectedLatitude(), playerOne.getSelectedLongitude()))
            .build();
      }
      if (latLngBounds != null) {
        mapboxMap.easeCamera(newLatLngBounds(latLngBounds, CAMERA_BOUNDS_PADDING), EASE_CAMERA_SPEED_IN_MS);
      }
    }
  }

  private void makeMarker(LatLng markerPoint, String title, String snippet, Icon chosenIcon) {
    Marker marker = mapboxMap.addMarker(new MarkerOptions()
        .position(markerPoint)
        .title(title)
        .snippet(snippet)
        .icon(chosenIcon));
    mapboxMap.selectMarker(marker);
  }

  private void initializeIcons() {
    playerOneIcon = IconFactory.getInstance(this).fromResource(R.drawable.player_one_icon);
    playerTwoIcon = IconFactory.getInstance(this).fromResource(R.drawable.player_two_icon);
    bullsEyeIcon = IconFactory.getInstance(this).fromResource(R.drawable.bullseye_outline_filled);
  }

  private void setOneOrTwoPlayerGame(Intent intent) {
    String typeOfGame = intent.getStringExtra(TYPE_OF_GAME_KEY);
    isSinglePlayerGame = typeOfGame.equals(ONE_PLAYER_GAME);
    isTwoPlayerGame = typeOfGame.equals(TWO_PLAYER_GAME);
  }

  private void getAndDisplayLocationToGuess() {
    int randomInt = new Random().nextInt(listOfCities.size()) + 1;
    Feature randomCityFromList = listOfCities.get(randomInt);
    randomTargetCity = new City();
    randomTargetCity.setCityName(randomCityFromList.getStringProperty("city"));
    String randomCityCoordinates = randomCityFromList.geometry().coordinates().toString();
    String stringLong = randomCityCoordinates.split(",")[0].replace("[", "");
    String stringLat = randomCityCoordinates.split(",")[1].replace("]", "").replace(" ", "");
    randomCityLocation = new Location("");
    randomCityLocation.setLatitude(Double.valueOf(stringLat));
    randomCityLocation.setLongitude(Double.valueOf(stringLong));
    randomTargetCity.setCityLocation(randomCityLocation);
    locationToGuess.setText(getResources().getString(R.string.location_to_guess, randomTargetCity.getCityName()));
  }

  // Uses Mapbox's Turf library to get distance
  private double checkDistanceBetweenTargetAndGuess(Player playerToCheck) {
    Point randomPoint = Point.fromLngLat(randomCityLocation.getLongitude(),
        randomCityLocation.getLatitude());
    Point selectedPoint = Point.fromLngLat(playerToCheck.getSelectedLongitude(),
        playerToCheck.getSelectedLatitude());
    return TurfMeasurement.distance(randomPoint, selectedPoint);
  }

  private void addTargetCityBullsEyeMarkerToMap(LatLng location, String title, Icon chosenIcon) {
    mapboxMap.addMarker(new MarkerOptions()
        .position(location)
        .title(title)
        .icon(chosenIcon));
  }

  private void calculateAndGivePointToWinner() {
    if (checkDistanceBetweenTargetAndGuess(playerOne) < checkDistanceBetweenTargetAndGuess(playerTwo)) {
      Snackbar.make(findViewById(android.R.id.content),
          getResources().getString(R.string.winner_announcement, playerOne.getPlayerName()),
          Snackbar.LENGTH_SHORT).show();
      playerOne.setPoints(playerOne.getPoints() + 1);
      displayPlayersPoints();
      flashTextAsPointIsAdded(playerOnePointsTextView);
    } else {
      Snackbar.make(findViewById(android.R.id.content),
          getResources().getString(R.string.winner_announcement, playerTwo.getPlayerName()),
          Snackbar.LENGTH_SHORT).show();
      playerTwo.setPoints(playerTwo.getPoints() + 1);
      displayPlayersPoints();
      flashTextAsPointIsAdded(playerTwoPointsTextView);
    }
    getAndDisplayLocationToGuess();
  }

  private void flashTextAsPointIsAdded(TextView textView) {
    Animation anim = new AlphaAnimation(0.0f, 1.0f);
    anim.setDuration(50); // Manage the blinking speed here
    anim.setStartOffset(1);
    anim.setRepeatMode(Animation.REVERSE);
    anim.setRepeatCount(8);
    textView.startAnimation(anim);
  }

  private void displayPlayersPoints() {
    if (isSinglePlayerGame) {
      playerOnePointsTextView.setVisibility(View.GONE);
      playerTwoPointsTextView.setVisibility(View.GONE);
    } else {
      if (isNewTwoPlayerGame()) {
        if (playerOne.getPlayerName() == null && !playerTwo.getPlayerName().isEmpty()) {
          setPlayerTextViews(playerOnePointsTextView, R.string.player_one_points, getString(R.string.default_player_one_name), 0);
          setPlayerTextViews(playerTwoPointsTextView, R.string.player_two_points, playerTwo.getPlayerName(), 0);
        }
        if (!playerTwo.getPlayerName().isEmpty() && playerTwo.getPlayerName().isEmpty()) {
          setPlayerTextViews(playerOnePointsTextView, R.string.player_one_points, playerOne.getPlayerName(), 0);
          setPlayerTextViews(playerTwoPointsTextView, R.string.player_two_points, getString(R.string.default_player_two_name), 0);
        }
        if (!playerTwo.getPlayerName().isEmpty() && !playerTwo.getPlayerName().isEmpty()) {
          setPlayerTextViews(playerOnePointsTextView, R.string.player_one_points, playerOne.getPlayerName(), 0);
          setPlayerTextViews(playerTwoPointsTextView, R.string.player_two_points, playerTwo.getPlayerName(), 0);
        }
      } else {
        if (playerOne.getPlayerName().isEmpty() && !playerTwo.getPlayerName().isEmpty()) {
          setPlayerTextViews(playerOnePointsTextView, R.string.player_one_points, getString(R.string.default_player_one_name), playerOne.getPoints());
          setPlayerTextViews(playerTwoPointsTextView, R.string.player_two_points, playerTwo.getPlayerName(), playerTwo.getPoints());
        }
        if (!playerTwo.getPlayerName().isEmpty() && playerTwo.getPlayerName().isEmpty()) {
          setPlayerTextViews(playerOnePointsTextView, R.string.player_one_points, playerOne.getPlayerName(), playerOne.getPoints());
          setPlayerTextViews(playerTwoPointsTextView, R.string.player_two_points, getString(R.string.default_player_two_name), playerTwo.getPoints());
        }
        if (!playerTwo.getPlayerName().isEmpty() && !playerTwo.getPlayerName().isEmpty()) {
          setPlayerTextViews(playerOnePointsTextView, R.string.player_one_points, playerOne.getPlayerName(), playerOne.getPoints());
          setPlayerTextViews(playerTwoPointsTextView, R.string.player_two_points, playerTwo.getPlayerName(), playerTwo.getPoints());
        }
      }
    }
  }

  @OnClick(R.id.check_answer_fab)
  public void checkAnswer(View view) {
    if (isSinglePlayerGame) {
      playerOneHasGuessed = false;
      Snackbar.make(findViewById(android.R.id.content),
          getResources().getString(R.string.player_guess_distance, checkDistanceBetweenTargetAndGuess(playerOne)),
          Snackbar.LENGTH_SHORT).show();
      getAndDisplayLocationToGuess();
    }
    if (isTwoPlayerGame) {
      calculateAndGivePointToWinner();
      playerOneHasGuessed = false;
      playerTwoHasGuessed = false;
    }
    mapboxMap.clear();
    checkAnswerFab.hide();
  }

  private boolean isNewTwoPlayerGame() {
    return playerOne.getPoints() == 0 && playerTwo.getPoints() == 0;
  }

  private void setPlayerTextViews(TextView view, int stringId, String playerName, int numOfPoints) {
    view.setText(getResources().getString(stringId, playerName, numOfPoints));
  }

  private void loadCities() throws IOException {
    try {
      // Use fromJson() method to convert GeoJSON file into a usable FeatureCollection
      FeatureCollection featureCollection = FeatureCollection.fromJson(loadGeoJsonFromAsset("cities.geojson"));
      GameActivity.listOfCities = featureCollection.features();
    } catch (Exception exception) {
      Log.d(TAG, "getFeatureCollectionFromJson: " + exception);
    }
  }

  private String loadGeoJsonFromAsset(String filename) {
    try {
      // Load GeoJSON file from local asset folder
      InputStream is = getAssets().open(filename);
      int size = is.available();
      byte[] buffer = new byte[size];
      is.read(buffer);
      is.close();
      return new String(buffer, "UTF-8");
    } catch (Exception exception) {
      Log.d(TAG, "Exception Loading GeoJSON: " + exception.toString());
      exception.printStackTrace();
      return null;
    }
  }

  // region activity lifecycle method overrides
  @Override
  public void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @Override
  public void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mapboxMap != null) {
      mapboxMap.removeOnMapClickListener(this);
    }
    mapView.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }
  //endregion
}
