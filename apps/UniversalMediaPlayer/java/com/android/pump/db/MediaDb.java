/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.pump.db;

import android.Manifest;
import android.content.ContentResolver;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.annotation.UiThread;
import androidx.collection.ArraySet;

import com.android.pump.concurrent.Executors;
import com.android.pump.util.Clog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@UiThread
public class MediaDb implements MediaProvider {
    private static final String TAG = Clog.tag(MediaDb.class);

    private final AtomicBoolean mLoaded = new AtomicBoolean();

    private final Executor mExecutor;

    private final AudioStore mAudioStore;
    private final VideoStore mVideoStore;
    private final DataProvider mDataProvider;

    private final List<Audio> mAudios = new ArrayList<>();
    private final List<Artist> mArtists = new ArrayList<>();
    private final List<Album> mAlbums = new ArrayList<>();
    private final List<Genre> mGenres = new ArrayList<>();
    private final List<Playlist> mPlaylists = new ArrayList<>();

    private final List<Movie> mMovies = new ArrayList<>();
    private final List<Series> mSeries = new ArrayList<>();
    private final List<Episode> mEpisodes = new ArrayList<>();
    private final List<Other> mOthers = new ArrayList<>();

    private final Set<UpdateCallback> mAudioUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mArtistUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mAlbumUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mGenreUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mPlaylistUpdateCallbacks = new ArraySet<>();

    private final Set<UpdateCallback> mMovieUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mSeriesUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mEpisodeUpdateCallbacks = new ArraySet<>();
    private final Set<UpdateCallback> mOtherUpdateCallbacks = new ArraySet<>();

    public interface UpdateCallback {
        void onItemsInserted(int index, int count);
        void onItemsUpdated(int index, int count);
        void onItemsRemoved(int index, int count);
    }

    public MediaDb(@NonNull ContentResolver contentResolver, @NonNull DataProvider dataProvider,
            @NonNull Executor executor) {
        Clog.i(TAG, "MediaDb(" + contentResolver + ", " + dataProvider + ", " + executor + ")");
        mDataProvider = dataProvider;
        mExecutor = executor;

        mAudioStore = new AudioStore(contentResolver, new AudioStore.ChangeListener() {
            @Override
            public void onAudiosAdded(@NonNull Collection<Audio> audios) {
                Executors.uiThreadExecutor().execute(() -> addAudios(audios));
            }

            @Override
            public void onArtistsAdded(@NonNull Collection<Artist> artists) {
                Executors.uiThreadExecutor().execute(() -> addArtists(artists));
            }

            @Override
            public void onAlbumsAdded(@NonNull Collection<Album> albums) {
                Executors.uiThreadExecutor().execute(() -> addAlbums(albums));
            }

            @Override
            public void onGenresAdded(@NonNull Collection<Genre> genres) {
                Executors.uiThreadExecutor().execute(() -> addGenres(genres));
            }

            @Override
            public void onPlaylistsAdded(@NonNull Collection<Playlist> playlists) {
                Executors.uiThreadExecutor().execute(() -> addPlaylists(playlists));
            }
        }, this);

        mVideoStore = new VideoStore(contentResolver, new VideoStore.ChangeListener() {
            @Override
            public void onMoviesAdded(@NonNull Collection<Movie> movies) {
                Executors.uiThreadExecutor().execute(() -> addMovies(movies));
            }

            @Override
            public void onSeriesAdded(@NonNull Collection<Series> series) {
                Executors.uiThreadExecutor().execute(() -> addSeries(series));
            }

            @Override
            public void onEpisodesAdded(@NonNull Collection<Episode> episodes) {
                Executors.uiThreadExecutor().execute(() -> addEpisodes(episodes));
            }

            @Override
            public void onOthersAdded(@NonNull Collection<Other> others) {
                Executors.uiThreadExecutor().execute(() -> addOthers(others));
            }
        }, this);
    }

    public void addAudioUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mAudioUpdateCallbacks, callback);
    }

    public void removeAudioUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mAudioUpdateCallbacks, callback);
    }

    public void addArtistUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mArtistUpdateCallbacks, callback);
    }

    public void removeArtistUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mArtistUpdateCallbacks, callback);
    }

    public void addAlbumUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mAlbumUpdateCallbacks, callback);
    }

    public void removeAlbumUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mAlbumUpdateCallbacks, callback);
    }

    public void addGenreUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mGenreUpdateCallbacks, callback);
    }

    public void removeGenreUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mGenreUpdateCallbacks, callback);
    }

    public void addPlaylistUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mPlaylistUpdateCallbacks, callback);
    }

    public void removePlaylistUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mPlaylistUpdateCallbacks, callback);
    }

    public void addMovieUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mMovieUpdateCallbacks, callback);
    }

    public void removeMovieUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mMovieUpdateCallbacks, callback);
    }

    public void addSeriesUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mSeriesUpdateCallbacks, callback);
    }

    public void removeSeriesUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mSeriesUpdateCallbacks, callback);
    }

    public void addEpisodeUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mEpisodeUpdateCallbacks, callback);
    }

    public void removeEpisodeUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mEpisodeUpdateCallbacks, callback);
    }

    public void addOtherUpdateCallback(@NonNull UpdateCallback callback) {
        addUpdateCallback(mOtherUpdateCallbacks, callback);
    }

    public void removeOtherUpdateCallback(@NonNull UpdateCallback callback) {
        removeUpdateCallback(mOtherUpdateCallbacks, callback);
    }

    @RequiresPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    public void load() {
        Clog.i(TAG, "load()");
        if (mLoaded.getAndSet(true)) {
            return;
        }

        mExecutor.execute(mAudioStore::load);
        mExecutor.execute(mVideoStore::load);
    }

    public @NonNull List<Audio> getAudios() {
        return Collections.unmodifiableList(mAudios);
    }
    public @NonNull List<Artist> getArtists() {
        return Collections.unmodifiableList(mArtists);
    }
    public @NonNull List<Album> getAlbums() {
        return Collections.unmodifiableList(mAlbums);
    }
    public @NonNull List<Genre> getGenres() {
        return Collections.unmodifiableList(mGenres);
    }
    public @NonNull List<Playlist> getPlaylists() {
        return Collections.unmodifiableList(mPlaylists);
    }

    public @NonNull List<Movie> getMovies() {
        return Collections.unmodifiableList(mMovies);
    }
    public @NonNull List<Series> getSeries() {
        return Collections.unmodifiableList(mSeries);
    }
    public @NonNull List<Episode> getEpisodes() {
        return Collections.unmodifiableList(mEpisodes);
    }
    public @NonNull List<Other> getOthers() {
        return Collections.unmodifiableList(mOthers);
    }

    public void loadData(@NonNull Audio audio) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (audio.isLoaded()) return;

        mExecutor.execute(() -> {
            boolean updated = mAudioStore.loadData(audio);

            audio.setLoaded();
            if (updated) {
                Executors.uiThreadExecutor().execute(() -> updateAudio(audio));
            }
        });
    }

    public void loadData(@NonNull Artist artist) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (artist.isLoaded()) return;

        mExecutor.execute(() -> {
            try {
                boolean updated = mDataProvider.populateArtist(artist);

                updated |= mAudioStore.loadData(artist);

                artist.setLoaded();
                if (updated) {
                    Executors.uiThreadExecutor().execute(() -> updateArtist(artist));
                }
            } catch (IOException e) {
                Clog.e(TAG, "Search for " + artist + " failed", e);
            }
        });
    }

    public void loadData(@NonNull Album album) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (album.isLoaded()) return;

        mExecutor.execute(() -> {
            try {
                boolean updated = mDataProvider.populateAlbum(album);

                updated |= mAudioStore.loadData(album);

                album.setLoaded();
                if (updated) {
                    Executors.uiThreadExecutor().execute(() -> updateAlbum(album));
                }
            } catch (IOException e) {
                Clog.e(TAG, "Search for " + album + " failed", e);
            }
        });
    }

    public void loadData(@NonNull Genre genre) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (genre.isLoaded()) return;

        mExecutor.execute(() -> {
            boolean updated = mAudioStore.loadData(genre);

            genre.setLoaded();
            if (updated) {
                Executors.uiThreadExecutor().execute(() -> updateGenre(genre));
            }
        });
    }

    public void loadData(@NonNull Playlist playlist) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (playlist.isLoaded()) return;

        mExecutor.execute(() -> {
            boolean updated = mAudioStore.loadData(playlist);

            playlist.setLoaded();
            if (updated) {
                Executors.uiThreadExecutor().execute(() -> updatePlaylist(playlist));
            }
        });
    }

    // TODO(b/123707018) Merge with loadData(episode)/loadData(other)
    public void loadData(@NonNull Movie movie) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (movie.isLoaded()) return;

        mExecutor.execute(() -> {
            try {
                boolean updated = mDataProvider.populateMovie(movie);

                updated |= mVideoStore.loadData(movie);

                movie.setLoaded();
                if (updated) {
                    Executors.uiThreadExecutor().execute(() -> updateMovie(movie));
                }
            } catch (IOException e) {
                Clog.e(TAG, "Search for " + movie + " failed", e);
            }
        });
    }

    public void loadData(@NonNull Series series) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (series.isLoaded()) return;

        mExecutor.execute(() -> {
            try {
                boolean updated = mDataProvider.populateSeries(series);

                updated |= mVideoStore.loadData(series);

                series.setLoaded();
                if (updated) {
                    Executors.uiThreadExecutor().execute(() -> updateSeries(series));
                }
            } catch (IOException e) {
                Clog.e(TAG, "Search for " + series + " failed", e);
            }
        });
    }

    // TODO(b/123707018) Merge with loadData(movie)/loadData(other)
    public void loadData(@NonNull Episode episode) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (episode.isLoaded()) return;

        mExecutor.execute(() -> {
            try {
                boolean updated = mDataProvider.populateEpisode(episode);

                updated |= mVideoStore.loadData(episode);

                episode.setLoaded();
                if (updated) {
                    Executors.uiThreadExecutor().execute(() -> updateEpisode(episode));
                }
            } catch (IOException e) {
                Clog.e(TAG, "Search for " + episode + " failed", e);
            }
        });
    }

    // TODO(b/123707018) Merge with loadData(movie)/loadData(episode)
    public void loadData(@NonNull Other other) {
        // TODO(b/123707632) Ensure no concurrent runs for the same item !!
        if (other.isLoaded()) return;

        mExecutor.execute(() -> {
            boolean updated = mVideoStore.loadData(other);

            other.setLoaded();
            if (updated) {
                Executors.uiThreadExecutor().execute(() -> updateOther(other));
            }
        });
    }

    @Override
    public @NonNull Audio getAudioById(long id) {
        for (Audio audio : mAudios) {
            if (audio.getId() == id) {
                return audio;
            }
        }
        throw new IllegalArgumentException("Audio with id " + id + " was not found");
    }

    @Override
    public @NonNull Artist getArtistById(long id) {
        for (Artist artist : mArtists) {
            if (artist.getId() == id) {
                return artist;
            }
        }
        throw new IllegalArgumentException("Artist with id " + id + " was not found");
    }

    @Override
    public @NonNull Album getAlbumById(long id) {
        for (Album album : mAlbums) {
            if (album.getId() == id) {
                return album;
            }
        }
        throw new IllegalArgumentException("Album with id " + id + " was not found");
    }

    @Override
    public @NonNull Genre getGenreById(long id) {
        for (Genre genre : mGenres) {
            if (genre.getId() == id) {
                return genre;
            }
        }
        throw new IllegalArgumentException("Genre with id " + id + " was not found");
    }

    @Override
    public @NonNull Playlist getPlaylistById(long id) {
        for (Playlist playlist : mPlaylists) {
            if (playlist.getId() == id) {
                return playlist;
            }
        }
        throw new IllegalArgumentException("Playlist with id " + id + " was not found");
    }

    @Override
    public @NonNull Movie getMovieById(long id) {
        for (Movie movie : mMovies) {
            if (movie.getId() == id) {
                return movie;
            }
        }
        throw new IllegalArgumentException("Movie with id " + id + " was not found");
    }

    @Override
    public @NonNull Series getSeriesById(@NonNull String title) {
        for (Series series : mSeries) {
            if (!series.hasYear() && series.getTitle().equals(title)) {
                return series;
            }
        }
        throw new IllegalArgumentException("Series '" + title + "' was not found");
    }

    @Override
    public @NonNull Series getSeriesById(@NonNull String title, int year) {
        for (Series series : mSeries) {
            if (series.hasYear() && series.getTitle().equals(title) && series.getYear() == year) {
                return series;
            }
        }
        throw new IllegalArgumentException("Series '" + title + "' (" + year + ") was not found");
    }

    @Override
    public @NonNull Episode getEpisodeById(long id) {
        for (Episode episode : mEpisodes) {
            if (episode.getId() == id) {
                return episode;
            }
        }
        throw new IllegalArgumentException("Episode with id " + id + " was not found");
    }

    @Override
    public @NonNull Other getOtherById(long id) {
        for (Other other : mOthers) {
            if (other.getId() == id) {
                return other;
            }
        }
        throw new IllegalArgumentException("Other with id " + id + " was not found");
    }

    private void addUpdateCallback(@NonNull Set<UpdateCallback> callbacks,
            @NonNull UpdateCallback callback) {
        if (!callbacks.add(callback)) {
            throw new IllegalArgumentException("Callback " + callback + " already added in " +
                    callbacks);
        }
    }

    private void removeUpdateCallback(@NonNull Set<UpdateCallback> callbacks,
            @NonNull UpdateCallback callback) {
        if (!callbacks.remove(callback)) {
            throw new IllegalArgumentException("Callback " + callback + " not found in " +
                    callbacks);
        }
    }

    private void addAudios(@NonNull Collection<Audio> audios) {
        int audiosIndex = mAudios.size();
        int audiosCount = 0;

        mAudios.addAll(audios);
        audiosCount += audios.size();

        if (audiosCount > 0) {
            for (UpdateCallback callback : mAudioUpdateCallbacks) {
                callback.onItemsInserted(audiosIndex, audiosCount);
            }
        }
    }

    private void addArtists(@NonNull Collection<Artist> artists) {
        int artistsIndex = mArtists.size();
        int artistsCount = 0;

        mArtists.addAll(artists);
        artistsCount += artists.size();

        if (artistsCount > 0) {
            for (UpdateCallback callback : mArtistUpdateCallbacks) {
                callback.onItemsInserted(artistsIndex, artistsCount);
            }
        }
    }

    private void addAlbums(@NonNull Collection<Album> albums) {
        int albumsIndex = mAlbums.size();
        int albumsCount = 0;

        mAlbums.addAll(albums);
        albumsCount += albums.size();

        if (albumsCount > 0) {
            for (UpdateCallback callback : mAlbumUpdateCallbacks) {
                callback.onItemsInserted(albumsIndex, albumsCount);
            }
        }
    }

    private void addGenres(@NonNull Collection<Genre> genres) {
        int genresIndex = mGenres.size();
        int genresCount = 0;

        mGenres.addAll(genres);
        genresCount += genres.size();

        if (genresCount > 0) {
            for (UpdateCallback callback : mGenreUpdateCallbacks) {
                callback.onItemsInserted(genresIndex, genresCount);
            }
        }
    }

    private void addPlaylists(@NonNull Collection<Playlist> playlists) {
        int playlistsIndex = mPlaylists.size();
        int playlistsCount = 0;

        mPlaylists.addAll(playlists);
        playlistsCount += playlists.size();

        if (playlistsCount > 0) {
            for (UpdateCallback callback : mPlaylistUpdateCallbacks) {
                callback.onItemsInserted(playlistsIndex, playlistsCount);
            }
        }
    }

    private void addMovies(@NonNull Collection<Movie> movies) {
        int moviesIndex = mMovies.size();
        int moviesCount = 0;

        mMovies.addAll(movies);
        moviesCount += movies.size();

        if (moviesCount > 0) {
            for (UpdateCallback callback : mMovieUpdateCallbacks) {
                callback.onItemsInserted(moviesIndex, moviesCount);
            }
        }
    }

    private void addSeries(@NonNull Collection<Series> series) {
        int seriesIndex = mSeries.size();
        int seriesCount = 0;

        mSeries.addAll(series);
        seriesCount += series.size();

        if (seriesCount > 0) {
            for (UpdateCallback callback : mSeriesUpdateCallbacks) {
                callback.onItemsInserted(seriesIndex, seriesCount);
            }
        }
    }

    private void addEpisodes(@NonNull Collection<Episode> episodes) {
        int episodesIndex = mEpisodes.size();
        int episodesCount = 0;

        mEpisodes.addAll(episodes);
        episodesCount += episodes.size();

        if (episodesCount > 0) {
            for (UpdateCallback callback : mEpisodeUpdateCallbacks) {
                callback.onItemsInserted(episodesIndex, episodesCount);
            }
        }
    }

    private void addOthers(@NonNull Collection<Other> others) {
        int othersIndex = mOthers.size();
        int othersCount = 0;

        mOthers.addAll(others);
        othersCount += others.size();

        if (othersCount > 0) {
            for (UpdateCallback callback : mOtherUpdateCallbacks) {
                callback.onItemsInserted(othersIndex, othersCount);
            }
        }
    }

    private void updateAudio(@NonNull Audio audio) {
        int index = mAudios.indexOf(audio);
        if (index != -1) {
            for (UpdateCallback callback : mAudioUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateArtist(@NonNull Artist artist) {
        int index = mArtists.indexOf(artist);
        if (index != -1) {
            for (UpdateCallback callback : mArtistUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateAlbum(@NonNull Album album) {
        int index = mAlbums.indexOf(album);
        if (index != -1) {
            for (UpdateCallback callback : mAlbumUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateGenre(@NonNull Genre genre) {
        int index = mGenres.indexOf(genre);
        if (index != -1) {
            for (UpdateCallback callback : mGenreUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updatePlaylist(@NonNull Playlist playlist) {
        int index = mPlaylists.indexOf(playlist);
        if (index != -1) {
            for (UpdateCallback callback : mPlaylistUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateMovie(@NonNull Movie movie) {
        int index = mMovies.indexOf(movie);
        if (index != -1) {
            for (UpdateCallback callback : mMovieUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateSeries(@NonNull Series series) {
        int index = mSeries.indexOf(series);
        if (index != -1) {
            for (UpdateCallback callback : mSeriesUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateEpisode(@NonNull Episode episode) {
        int index = mEpisodes.indexOf(episode);
        if (index != -1) {
            for (UpdateCallback callback : mEpisodeUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }

    private void updateOther(@NonNull Other other) {
        int index = mOthers.indexOf(other);
        if (index != -1) {
            for (UpdateCallback callback : mOtherUpdateCallbacks) {
                callback.onItemsUpdated(index, 1);
            }
        }
    }
}
