CREATE INDEX idx_page_parent_inheritance ON page (parent, inherit_permissions, deleted);
CREATE INDEX idx_group_member_lookup ON group_member (member_id, member_type, group_id);
