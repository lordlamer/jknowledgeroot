package org.knowledgeroot.app.page.domain;

import java.util.List;

public interface PagePermissionDao {
    /**
     * List all permissions for a page
     * @param pageId The page ID
     * @return List of permissions
     */
    List<PagePermission> listPermissionsByPageId(PageId pageId);

    /**
     * Check if a user has specific permission for a page
     * @param pageId The page ID
     * @param userId The user ID
     * @param minPermissionLevel Minimum required permission level
     * @return true if user has permission, false otherwise
     */
    boolean hasUserPermission(PageId pageId, Integer userId, PagePermission.PermissionLevel minPermissionLevel);

    /**
     * Create a new permission
     * @param pagePermission The permission to create
     */
    void createPermission(PagePermission pagePermission);

    /**
     * Update an existing permission
     * @param pagePermission The permission to update
     */
    void updatePermission(PagePermission pagePermission);

    /**
     * Delete a permission
     * @param permissionId The permission ID
     */
    void deletePermission(Integer permissionId);

    /**
     * Create default permissions for a new page
     * @param pageId The page ID
     * @param creatorId The user ID of the creator
     */
    void createDefaultPermissions(PageId pageId, Integer creatorId);
}