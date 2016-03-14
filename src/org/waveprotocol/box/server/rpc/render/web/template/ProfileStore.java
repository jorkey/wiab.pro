package org.waveprotocol.box.server.rpc.render.web.template;

import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.wave.api.ParticipantProfile;

import java.util.Collection;
import java.util.Map;

public class ProfileStore {

  public Map<String, ParticipantProfile> getProfiles(Collection<String> participants) {
    Cache<String, ParticipantProfile> profiles =
        CacheBuilder.newBuilder().build(CacheLoader.from(new Function<String, ParticipantProfile>() {
      @Override
      public ParticipantProfile apply(String key) {
        return new ParticipantProfile(key, "", "");
      }
    }));
    return profiles.asMap();
  }
}
