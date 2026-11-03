-- Interface languages (R57): user.language now holds a supported code (en, de, fr, es, it, nl) or the
-- empty string for "follow the browser". Until now every account got the placeholder en_US, which
-- nobody chose; such values become automatic so that existing users see their browser language.
UPDATE user SET language = '' WHERE language NOT IN ('en', 'de', 'fr', 'es', 'it', 'nl');
