package com.indecisive_gaming.zombievillage;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public class Main extends ApplicationAdapter {
    private static final float WORLD_X = 24;
    private static final float WORLD_Y = 150;
    private static final float WORLD_W = 384;
    private static final float WORLD_H = 470;

    private final List<Button> menuButtons = new ArrayList<>();

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private final List<Villager> villagers = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();

    private Ruin ruin;
    private ConstructionSite constructionSite;

    private int storedScrap = 0;
    private int metal = 0;
    private int cloth = 0;
    private int parts = 0;

    private int shelterLevel = 0;
    private int storageLevel = 0;
    private int toolsLevel = 0;

    private float processorTimer = 0f;
    private String message = "Build shelter, then storage.";
    private boolean buildMenuOpen = false;

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        ruin = new Ruin(WORLD_X + 50, WORLD_Y + 360, 220);
        villagers.add(new Villager(WORLD_X + 190, WORLD_Y + 95));
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleInput();
        update(delta);
        draw();
    }

    private void handleInput() {
        if (!Gdx.input.justTouched()) {
            return;
        }

        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();

        if (inside(x, y, 16, 18, 92, 44)) {
            buildMenuOpen = !buildMenuOpen;
            updateMenuButtons();
            return;
        }

        if (!buildMenuOpen) {
            return;
        }

        updateMenuButtons();
        for (Button button : menuButtons) {
            if (inside(x, y, button.x, button.y, button.w, button.h)) {
                button.action.run();
                return;
            }
        }
    }

    private void startConstruction(BuildingType type) {
        if (constructionSite != null) {
            message = "Finish current build first.";
            return;
        }

        if (hasBuilding(type)) {
            message = type.label + " already built.";
            return;
        }

        if (type == BuildingType.PROCESSOR && !hasBuilding(BuildingType.STORAGE)) {
            message = "Storage is needed before processing.";
            return;
        }

        if (type == BuildingType.TOOLS && !hasBuilding(BuildingType.PROCESSOR)) {
            message = "Processor is needed before tools.";
            return;
        }

        constructionSite = new ConstructionSite(type, buildX(type), buildY(type));
        buildMenuOpen = false;
        message = "Villagers are hauling scrap for " + type.label + ".";
    }

    private void upgradeShelter() {
        if (shelterLevel == 0) {
            message = "Build shelter first.";
            return;
        }

        int costMetal = 3 + shelterLevel * 2;
        int costCloth = 2 + shelterLevel;
        if (metal < costMetal || cloth < costCloth) {
            message = "Shelter upgrade needs " + costMetal + " metal and " + costCloth + " cloth.";
            return;
        }

        metal -= costMetal;
        cloth -= costCloth;
        shelterLevel++;
        spawnVillagersToCap();
        message = "Shelter upgraded. Villager cap is now " + villagerCap() + ".";
    }

    private void upgradeTools() {
        if (!hasBuilding(BuildingType.TOOLS)) {
            message = "Build tools building first.";
            return;
        }

        int costMetal = 2 + toolsLevel * 2;
        int costParts = 2 + toolsLevel;
        if (metal < costMetal || parts < costParts) {
            message = "Tool upgrade needs " + costMetal + " metal and " + costParts + " parts.";
            return;
        }

        metal -= costMetal;
        parts -= costParts;
        toolsLevel++;
        message = "Tools upgraded. Scavenging is faster.";
    }

    private void update(float delta) {
        updateProcessor(delta);
        updateMenuButtons();

        for (Villager villager : villagers) {
            villager.update(delta, ruin, constructionSite, storage(), hasBuilding(BuildingType.STORAGE),
                storedScrap, storageCapacity(), toolsLevel, WORLD_X, WORLD_Y, WORLD_W, WORLD_H);

            if (villager.foundRuin) {
                villager.foundRuin = false;
                ruin = new Ruin(
                    WORLD_X + 35 + (float) Math.random() * (WORLD_W - 90),
                    WORLD_Y + 330 + (float) Math.random() * 80,
                    120 + (int) (Math.random() * 120)
                );
                message = "Excursion found a fresh ruin.";
            }

            if (villager.deliveredScrap) {
                villager.deliveredScrap = false;

                if (constructionSite != null && constructionSite.needsScrap()) {
                    constructionSite.deliveredScrap++;
                } else if (hasBuilding(BuildingType.STORAGE) && storedScrap < storageCapacity()) {
                    storedScrap++;
                }
            }

            if (villager.finishedConstruction) {
                villager.finishedConstruction = false;
                finishConstruction();
                break;
            }
        }
    }

    private void updateMenuButtons() {
        menuButtons.clear();
        if (!buildMenuOpen) {
            return;
        }

        addBuildButton(BuildingType.SHELTER);
        addBuildButton(BuildingType.STORAGE);
        addBuildButton(BuildingType.PROCESSOR);
        addBuildButton(BuildingType.TOOLS);

        if (shelterLevel > 0) {
            addMenuButton("Up Shelter", this::upgradeShelter);
        }

        if (hasBuilding(BuildingType.TOOLS)) {
            addMenuButton("Up Tools", this::upgradeTools);
        }
    }

    private void addBuildButton(BuildingType type) {
        if (hasBuilding(type)) {
            return;
        }

        if (type == BuildingType.PROCESSOR && !hasBuilding(BuildingType.STORAGE)) {
            return;
        }

        if (type == BuildingType.TOOLS && !hasBuilding(BuildingType.PROCESSOR)) {
            return;
        }

        addMenuButton(type.label, () -> startConstruction(type));
    }

    private void addMenuButton(String label, Runnable action) {
        int index = menuButtons.size();
        float x = 118 + (index % 2) * 150;
        float y = 18 + (index / 2) * 48;
        menuButtons.add(new Button(label, x, y, 140, 42, action));
    }

    private void updateProcessor(float delta) {
        if (!hasBuilding(BuildingType.PROCESSOR) || storedScrap <= 0) {
            return;
        }

        processorTimer += delta;
        float interval = Math.max(1.4f, 4f - toolsLevel * 0.35f);
        if (processorTimer < interval) {
            return;
        }

        processorTimer = 0f;
        storedScrap--;
        double roll = Math.random();
        if (roll < 0.45) {
            metal++;
            message = "Processor recovered metal.";
        } else if (roll < 0.8) {
            cloth++;
            message = "Processor recovered cloth.";
        } else {
            parts++;
            message = "Processor recovered parts.";
        }
    }

    private void finishConstruction() {
        Building building = new Building(constructionSite.type, constructionSite.x, constructionSite.y);
        buildings.add(building);

        if (constructionSite.type == BuildingType.SHELTER) {
            shelterLevel++;
            spawnVillagersToCap();
        } else if (constructionSite.type == BuildingType.STORAGE) {
            storageLevel++;
        }

        message = constructionSite.type.label + " complete.";
        constructionSite = null;
    }

    private void spawnVillagersToCap() {
        while (villagers.size() < villagerCap()) {
            villagers.add(new Villager(WORLD_X + 250 + villagers.size() * 12, WORLD_Y + 95));
        }
    }

    private int villagerCap() {
        return Math.max(1, shelterLevel + 1);
    }

    private int storageCapacity() {
        return hasBuilding(BuildingType.STORAGE) ? 30 + storageLevel * 30 : 0;
    }

    private Building storage() {
        for (Building building : buildings) {
            if (building.type == BuildingType.STORAGE) {
                return building;
            }
        }
        return null;
    }

    private boolean hasBuilding(BuildingType type) {
        for (Building building : buildings) {
            if (building.type == type) {
                return true;
            }
        }
        return false;
    }

    private float buildX(BuildingType type) {
        switch (type) {
            case SHELTER:
                return WORLD_X + 275;
            case STORAGE:
                return WORLD_X + 170;
            case PROCESSOR:
                return WORLD_X + 275;
            case TOOLS:
            default:
                return WORLD_X + 275;
        }
    }

    private float buildY(BuildingType type) {
        switch (type) {
            case SHELTER:
                return WORLD_Y + 260;
            case STORAGE:
                return WORLD_Y + 135;
            case PROCESSOR:
                return WORLD_Y + 135;
            case TOOLS:
            default:
                return WORLD_Y + 70;
        }
    }

    private void draw() {
        Gdx.gl.glClearColor(0.06f, 0.07f, 0.06f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0.14f, 0.15f, 0.13f, 1);
        shapes.rect(WORLD_X, WORLD_Y, WORLD_W, WORLD_H);

        shapes.setColor(0.10f, 0.11f, 0.10f, 1);
        shapes.rect(0, 0, Gdx.graphics.getWidth(), 136);
        shapes.rect(0, Gdx.graphics.getHeight() - 118, Gdx.graphics.getWidth(), 118);

        ruin.draw(shapes);

        for (Building building : buildings) {
            building.draw(shapes);
        }

        if (constructionSite != null) {
            constructionSite.draw(shapes);
        }

        for (Villager villager : villagers) {
            villager.draw(shapes);
        }

        drawButton(16, 18, 92, 44, buildMenuOpen ? Color.GRAY : Color.DARK_GRAY);
        if (buildMenuOpen) {
            for (Button button : menuButtons) {
                drawButton(button.x, button.y, button.w, button.h, Color.DARK_GRAY);
            }
        }

        shapes.end();

        batch.begin();

        float top = Gdx.graphics.getHeight() - 18;
        font.draw(batch, "Scrap " + storedScrap + "/" + storageCapacity(), 18, top);
        font.draw(batch, "Metal " + metal, 18, top - 24);
        font.draw(batch, "Cloth " + cloth, 118, top - 24);
        font.draw(batch, "Parts " + parts, 218, top - 24);
        font.draw(batch, "Villagers " + villagers.size() + "/" + villagerCap(), 18, top - 48);
        font.draw(batch, "Tools " + toolsLevel, 160, top - 48);
        font.draw(batch, "Ruin " + ruin.scrapRemaining, 250, top - 48);
        font.draw(batch, message, 18, top - 84);

        font.draw(batch, "Build", 45, 47);
        if (buildMenuOpen) {
            for (Button button : menuButtons) {
                font.draw(batch, button.label, button.x + 12, button.y + 27);
            }
        }

        if (constructionSite != null) {
            font.draw(batch, constructionSite.type.label, constructionSite.x - 8, constructionSite.y + 58);
            font.draw(batch, constructionSite.deliveredScrap + "/" + constructionSite.requiredScrap,
                constructionSite.x + 5, constructionSite.y + 42);
        }

        batch.end();

        Gdx.graphics.setTitle("ZombieVillage | Scrap " + storedScrap + "/" + storageCapacity()
            + " | Villagers " + villagers.size() + "/" + villagerCap());
    }

    private void drawButton(float x, float y, float w, float h, Color color) {
        shapes.setColor(color);
        shapes.rect(x, y, w, h);
    }

    private boolean inside(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    static class Button {
        String label;
        float x;
        float y;
        float w;
        float h;
        Runnable action;

        Button(String label, float x, float y, float w, float h, Runnable action) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    enum BuildingType {
        SHELTER("Shelter", 10, 5f),
        STORAGE("Storage", 8, 4f),
        PROCESSOR("Processor", 14, 7f),
        TOOLS("Tools", 12, 6f);

        final String label;
        final int scrapCost;
        final float workCost;

        BuildingType(String label, int scrapCost, float workCost) {
            this.label = label;
            this.scrapCost = scrapCost;
            this.workCost = workCost;
        }
    }

    static class Ruin {
        float x;
        float y;
        int scrapRemaining;

        Ruin(float x, float y, int scrapRemaining) {
            this.x = x;
            this.y = y;
            this.scrapRemaining = scrapRemaining;
        }

        void draw(ShapeRenderer shapes) {
            shapes.setColor(Color.LIGHT_GRAY);
            shapes.rect(x, y, 28, 16);
            shapes.setColor(Color.GRAY);
            shapes.rect(x + 10, y + 16, 16, 20);
            shapes.rect(x - 10, y + 6, 16, 18);
        }
    }

    static class ConstructionSite {
        BuildingType type;
        float x;
        float y;
        int deliveredScrap = 0;
        int requiredScrap;
        float workDone = 0f;
        float workRequired;

        ConstructionSite(BuildingType type, float x, float y) {
            this.type = type;
            this.x = x;
            this.y = y;
            requiredScrap = type.scrapCost;
            workRequired = type.workCost;
        }

        boolean needsScrap() {
            return deliveredScrap < requiredScrap;
        }

        boolean isComplete() {
            return deliveredScrap >= requiredScrap && workDone >= workRequired;
        }

        void draw(ShapeRenderer shapes) {
            shapes.setColor(Color.ORANGE);
            shapes.rect(x, y, 42, 30);
            shapes.setColor(Color.YELLOW);
            shapes.rect(x, y + 33, 42 * Math.min(1f, workDone / workRequired), 5);
        }
    }

    static class Building {
        BuildingType type;
        float x;
        float y;

        Building(BuildingType type, float x, float y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }

        void draw(ShapeRenderer shapes) {
            switch (type) {
                case SHELTER:
                    shapes.setColor(Color.BROWN);
                    shapes.rect(x, y, 48, 34);
                    shapes.setColor(Color.DARK_GRAY);
                    shapes.triangle(x - 4, y + 34, x + 24, y + 55, x + 52, y + 34);
                    break;
                case STORAGE:
                    shapes.setColor(0.45f, 0.32f, 0.18f, 1);
                    shapes.rect(x, y, 46, 32);
                    shapes.setColor(Color.LIGHT_GRAY);
                    shapes.rect(x + 6, y + 12, 34, 5);
                    break;
                case PROCESSOR:
                    shapes.setColor(0.35f, 0.35f, 0.38f, 1);
                    shapes.rect(x, y, 46, 36);
                    shapes.setColor(Color.RED);
                    shapes.rect(x + 31, y + 36, 8, 24);
                    break;
                case TOOLS:
                    shapes.setColor(0.22f, 0.27f, 0.32f, 1);
                    shapes.rect(x, y, 46, 32);
                    shapes.setColor(Color.ORANGE);
                    shapes.rect(x + 10, y + 14, 26, 5);
                    break;
            }
        }
    }

    static class Villager {
        enum State {
            WANDERING,
            TO_RUIN,
            SCAVENGING,
            TO_PROJECT,
            TO_STORAGE,
            WORKING,
            EXCURSION
        }

        float x;
        float y;
        float targetX;
        float targetY;

        boolean carrying = false;
        boolean deliveredScrap = false;
        boolean finishedConstruction = false;
        boolean foundRuin = false;

        float scavengeTimer = 0f;
        float excursionTimer = 0f;
        State state = State.WANDERING;

        Villager(float x, float y) {
            this.x = x;
            this.y = y;
            pickWanderTarget(WORLD_X, WORLD_Y, WORLD_W, WORLD_H);
        }

        void update(float delta, Ruin ruin, ConstructionSite site, Building storage, boolean storageBuilt,
                    int storedScrap, int storageCapacity, int toolsLevel,
                    float vx, float vy, float vw, float vh) {
            float speed = 65f + toolsLevel * 8f;

            switch (state) {
                case WANDERING:
                    if (site != null && site.needsScrap()) {
                        state = ruin.scrapRemaining > 0 ? State.TO_RUIN : State.EXCURSION;
                        excursionTimer = 4.5f;
                        return;
                    }

                    if (site != null) {
                        state = State.WORKING;
                        return;
                    }

                    if (storageBuilt && storedScrap < storageCapacity && ruin.scrapRemaining > 0) {
                        state = State.TO_RUIN;
                        return;
                    }

                    if (storageBuilt && storedScrap < storageCapacity && ruin.scrapRemaining <= 0) {
                        state = State.EXCURSION;
                        excursionTimer = 4.5f;
                        return;
                    }

                    moveToward(delta, speed);
                    if (distanceTo(targetX, targetY) < 4) {
                        pickWanderTarget(vx, vy, vw, vh);
                    }
                    break;

                case TO_RUIN:
                    if (site != null && !site.needsScrap()) {
                        state = State.WORKING;
                        return;
                    }

                    if (ruin.scrapRemaining <= 0) {
                        state = State.EXCURSION;
                        excursionTimer = 4.5f;
                        return;
                    }

                    targetX = ruin.x;
                    targetY = ruin.y;
                    moveToward(delta, speed);

                    if (distanceTo(ruin.x, ruin.y) < 7 && ruin.scrapRemaining > 0) {
                        state = State.SCAVENGING;
                        scavengeTimer = Math.max(0.55f, 2f - toolsLevel * 0.18f);
                    }
                    break;

                case SCAVENGING:
                    scavengeTimer -= delta;
                    if (scavengeTimer <= 0) {
                        ruin.scrapRemaining--;
                        carrying = true;
                        state = site != null && site.needsScrap() ? State.TO_PROJECT : State.TO_STORAGE;
                    }
                    break;

                case TO_PROJECT:
                    if (site == null) {
                        carrying = false;
                        state = State.WANDERING;
                        return;
                    }

                    targetX = site.x;
                    targetY = site.y;
                    moveToward(delta, speed);

                    if (distanceTo(site.x, site.y) < 7) {
                        if (carrying) {
                            carrying = false;
                            deliveredScrap = true;
                        }

                        state = site.needsScrap() ? State.TO_RUIN : State.WORKING;
                    }
                    break;

                case TO_STORAGE:
                    if (storage == null) {
                        carrying = false;
                        state = State.WANDERING;
                        return;
                    }

                    targetX = storage.x;
                    targetY = storage.y;
                    moveToward(delta, speed);

                    if (distanceTo(storage.x, storage.y) < 7) {
                        if (carrying) {
                            carrying = false;
                            deliveredScrap = true;
                        }
                        state = State.WANDERING;
                    }
                    break;

                case WORKING:
                    if (site == null) {
                        state = State.WANDERING;
                        return;
                    }

                    site.workDone += delta * (1f + toolsLevel * 0.12f);
                    if (site.isComplete()) {
                        finishedConstruction = true;
                        state = State.WANDERING;
                    }
                    break;

                case EXCURSION:
                    targetX = vx + vw - 8;
                    targetY = vy + vh * 0.5f;
                    moveToward(delta, speed);
                    excursionTimer -= delta;

                    if (excursionTimer <= 0f) {
                        foundRuin = true;
                        state = State.WANDERING;
                        pickWanderTarget(vx, vy, vw, vh);
                    }
                    break;
            }

            x = Math.max(vx + 5, Math.min(x, vx + vw - 5));
            y = Math.max(vy + 5, Math.min(y, vy + vh - 5));
        }

        void moveToward(float delta, float speed) {
            float dx = targetX - x;
            float dy = targetY - y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len > 0.001f) {
                x += dx / len * speed * delta;
                y += dy / len * speed * delta;
            }
        }

        void pickWanderTarget(float vx, float vy, float vw, float vh) {
            targetX = vx + 30 + (float) Math.random() * (vw - 60);
            targetY = vy + 30 + (float) Math.random() * (vh - 60);
        }

        float distanceTo(float tx, float ty) {
            float dx = tx - x;
            float dy = ty - y;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        void draw(ShapeRenderer shapes) {
            if (state == State.SCAVENGING || state == State.EXCURSION) {
                return;
            }

            shapes.setColor(carrying ? Color.YELLOW : Color.GREEN);
            shapes.rect(x, y, 7, 7);
        }
    }
}
