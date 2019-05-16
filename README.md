PlayerUUDCache
==========

This plugin provides an API to quickly resolve player names to UUIDs and vice versa. It uses an in memory cache as first level and a second level file or MySQL cache. For entries not found in the cache Mojang can be queried. All querys can be done asynchronously to avoid a negative performance impact.

If you have a network of multiple servers you can use the same MySQL database for all instances so cached results are shared.

Development Builds
==========
You can download development builds [from our Jenkins server](https://www.iani.de/jenkins/job/PlayerUUIDCache/).

Using this API your plugins
==========
To add this API as a maven dependency, add the following to your pom.xml:

In the repositorys section:

        <repository>
            <id>brokko-repo</id>
            <url>https://www.iani.de/nexus/content/groups/public</url>
        </repository>

In the dependencies section:

        <dependency>
            <groupId>de.iani.cubeside</groupId>
            <artifactId>PlayerUUIDCache</artifactId>
            <version>1.5.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>

Now you can use it in your plugin like this:

Add a dependency in the plugin.yml:

    depend: [PlayerUUIDCache]

Get the reference in the code:

    private PlayerUUIDCacheAPI playerUuidCache;
    public void onEnable() {
        playerUuidCache = getServer().getServicesManager().load(PlayerUUIDCacheAPI.class);
        [...]
    }

Use the reference (example):

    CachedPlayer cachedPlayer = playerUuidCache.getPlayerFromNameOrUUID(name);

For the complete API [have a look here](https://github.com/Brokkonaut/PlayerUUIDCache/blob/master/src/main/java/de/iani/playerUUIDCache/PlayerUUIDCacheAPI.java).