package de.unijena.bioinf.chemdb;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.PrimitiveIterator;
import java.util.stream.Collectors;

public class PubmedLinks implements Comparable<PubmedLinks>, Iterable<Integer> {
    public static final PubmedLinks EMPTY_LINKS = new PubmedLinks() {
        @Override
        public @Nullable URI getPubmedLink() {
            return null;
        }
    };


    private final int[] ids;
    private URI linkCache = null;

    public PubmedLinks(@NotNull TIntSet ids) {
        this.ids = ids.toArray();
        Arrays.sort(this.ids);
    }

    public PubmedLinks(@NotNull Collection<Integer> ids) {
        this.ids = ids.stream().sorted().mapToInt(it -> it).toArray();
    }

    public PubmedLinks(@NotNull int... ids) {
        this.ids = ids.clone();
        Arrays.sort(ids);
    }


    @NotNull
    public int[] getCopyOfPubmedIDs() {
        return ids.clone();
    }

    public int getNumberOfPubmedIDs() {
        return ids.length;
    }

    @Nullable
    public URI getPubmedLink() {
        if (linkCache == null)
            linkCache = createPubMedSearchLink();
        return linkCache;
    }

    @Override
    public int compareTo(@NotNull PubmedLinks o) {
        return Integer.compare(getNumberOfPubmedIDs(), o.getNumberOfPubmedIDs());
    }

    private URI createPubMedSearchLink() {
        if (ids.length < 1)
            return null;
        int to = Math.min(ids.length, 500);
        StringBuilder url = new StringBuilder("https://www.ncbi.nlm.nih.gov/pubmed/?term=");
        for (int i = ids.length - 1; i >= ids.length - to; i--) {
            url.append(ids[i]).append("%2C+");
        }
        try {
            return new URL(url.toString()).toURI();
        } catch (URISyntaxException | MalformedURLException e) {
            LoggerFactory.getLogger(getClass()).error("Illegal pubmed link", e);
            return null;
        }
    }

    @NotNull
    @Override
    public PrimitiveIterator.OfInt iterator() {
        return Arrays.stream(ids).iterator();
    }

    @Override
    public String toString() {
        return Arrays.stream(this.ids).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    public static @NotNull PubmedLinks fromString(String idList) {
        final TIntHashSet ids = new TIntHashSet();

        if (idList != null && !idList.isEmpty()) {
            for (String num : idList.split(",")) {
                try {
                    ids.add(Integer.parseInt(num));
                } catch (NumberFormatException e) {
                    LoggerFactory.getLogger(PubmedLinks.class).warn("Could not parse Pubmed ID '" + num + "'. Skipping this entry!", e);
                }
            }
        }
        return new PubmedLinks(ids);
    }
}
