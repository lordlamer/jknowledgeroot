package org.knowledgeroot.app.acl;

import org.knowledgeroot.app.acl.resource.BaseResource;
import org.knowledgeroot.app.acl.resource.ResourceInterface;
import org.knowledgeroot.app.acl.role.BaseRole;
import org.knowledgeroot.app.acl.role.RoleInterface;

import java.util.List;

public interface AclInterface {
	//
	//	ROLE HANDLING
	//

	/**
	 * check if role exists
	 *
	 * @param role
	 * @return
	 */
	boolean hasRole(RoleInterface role);

	/**
	 * add role
	 *
	 * @param role
	 */
	void addRole(RoleInterface role) throws AclException;

	/**
	 * add role with parent roles
	 *
	 * @param role
	 * @param parents
	 */
	void addRole(RoleInterface role, List<BaseRole> parents) throws AclException;

	/**
	 * remove role
	 *
	 * @param role
	 * @return
	 */
	void removeRole(RoleInterface role) throws AclException;

	/**
	 * remove role and delete all acl entries
	 *
	 * @param role
	 * @param removeAcl
	 * @return
	 */
	void removeRole(RoleInterface role, boolean removeAcl) throws AclException;

	/**
	 * get all roles
	 *
	 * @return
	 */
	List<BaseRole> getRoles() throws AclException;

	//
	//	RESOURCE HANDLING
	//

	/**
	 * check if resource exists
	 *
	 * @param resource
	 * @return
	 */
	boolean hasResource(ResourceInterface resource);

	/**
	 * add resource
	 *
	 * @param resource
	 */
	void addResource(ResourceInterface resource) throws AclException;

	/**
	 * remove resource
	 *
	 * @param resource
	 */
	void removeResource(ResourceInterface resource) throws AclException;

	/**
	 * remove resource and all acl entries
	 *
	 * @param resource
	 * @param removeAcl
	 */
	void removeResource(ResourceInterface resource, boolean removeAcl) throws AclException;

	/**
	 * get all resources
	 *
	 * @return
	 */
	List<BaseResource> getResources() throws AclException;

	//
	//	PERMISSIONS
	//

	/**
	 * clear acl entries by role
	 *
	 * @param role
	 */
	void clearByRole(RoleInterface role) throws AclException;

	/**
	 * clear acl entries by resource
	 *
	 * @param resource
	 */
	void clearByResource(ResourceInterface resource) throws AclException;

	/**
	 * create allow acl entry
	 *
	 * @param role
	 * @param resource
	 * @param action
	 */
	void allow(RoleInterface role, ResourceInterface resource, String action) throws AclException;

	/**
	 * create deny acl entry
	 *
	 * @param role
	 * @param resource
	 * @param action
	 */
	void deny(RoleInterface role, ResourceInterface resource, String action) throws AclException;

	/**
	 * remove acl entry
	 *
	 * @param role
	 * @param resource
	 * @param action
	 */
	void remove(RoleInterface role, ResourceInterface resource, String action) throws AclException;

	/**
	 * check if acl entry exists
	 *
	 * @param role
	 * @param resource
	 * @param action
	 * @return
	 */
	boolean hasRule(RoleInterface role, ResourceInterface resource, String action);

	/**
	 * check if role is allowed to access resource with given action
	 *
	 * @param role
	 * @param resource
	 * @param action
	 * @return
	 */
	boolean isAllowed(RoleInterface role, ResourceInterface resource, String action) throws AclException;

	/**
	 * check if role is allowed to access resource with given action
	 * if strict is true parents will not be checked
	 *
	 * @param role
	 * @param resource
	 * @param action
	 * @param strict
	 * @return
	 */
	boolean isAllowed(RoleInterface role, ResourceInterface resource, String action, boolean strict) throws AclException;

	//
	//	HELPER
	//

	/**
	 * clear complete acl store with all roles, resources and entries
	 */
	void truncateAll() throws AclException;
}