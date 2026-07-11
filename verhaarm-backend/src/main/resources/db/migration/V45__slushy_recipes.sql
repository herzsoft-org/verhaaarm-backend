CREATE TABLE IF NOT EXISTS slushy_recipes (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_slushy_recipes_created_by_user_id ON slushy_recipes (created_by_user_id);

DROP TRIGGER IF EXISTS trg_slushy_recipes_updated_at ON slushy_recipes;

CREATE TRIGGER trg_slushy_recipes_updated_at
    BEFORE UPDATE ON slushy_recipes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS slushy_recipe_ingredients (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES slushy_recipes(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    amount TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_slushy_recipe_ingredients_recipe_id ON slushy_recipe_ingredients (recipe_id);

CREATE TABLE IF NOT EXISTS slushy_recipe_ratings (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES slushy_recipes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stars SMALLINT NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT slushy_recipe_ratings_stars_check
        CHECK (stars BETWEEN 1 AND 5),
    CONSTRAINT uq_slushy_recipe_ratings_recipe_user
        UNIQUE (recipe_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_slushy_recipe_ratings_recipe_id ON slushy_recipe_ratings (recipe_id);

DROP TRIGGER IF EXISTS trg_slushy_recipe_ratings_updated_at ON slushy_recipe_ratings;

CREATE TRIGGER trg_slushy_recipe_ratings_updated_at
    BEFORE UPDATE ON slushy_recipe_ratings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
