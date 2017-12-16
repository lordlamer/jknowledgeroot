package org.knowledgeroot.app.acl.impl.database;

import org.knowledgeroot.app.acl.AclException;
import org.knowledgeroot.app.acl.AclInterface;
import org.knowledgeroot.app.acl.resource.BaseResource;
import org.knowledgeroot.app.acl.resource.ResourceInterface;
import org.knowledgeroot.app.acl.role.BaseRole;
import org.knowledgeroot.app.acl.role.RoleInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AclImpl implements AclInterface {
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * check if role exists
	 *
	 * @param role
	 * @return
	 */
	@Override
	public boolean hasRole(RoleInterface role) {
		try {
			jdbcTemplate.queryForObject(
				"SELECT `id` FROM `acl_role` WHERE `role` = ?",
				Long.class, role.getRoleId()
			);
		} catch(EmptyResultDataAccessException e) {
			return false;
		}

		return true;
	}

	/**
	 * add role
	 *
	 * @param role
	 */
	@Override
	public void addRole(RoleInterface role) throws AclException {
		addRole(role, null);
	}

	/**
	 * add role with parent roles
	 *
	 * @param role
	 * @param parents
	 */
	@Override
	public void addRole(RoleInterface role, List<BaseRole> parents) throws AclException {
		// check if role already exists
		if(hasRole(role))
			throw new AclException("Role already exists: " + role.getRoleId());

		// check if parent roles exists
		if(parents != null) {
			for (RoleInterface parentRole : parents) {
				// check if role already exists
				if (!hasRole(parentRole))
					throw new AclException("Role does not exists: " + parentRole.getRoleId());
			}
		}

		// create role entry
		try {
			jdbcTemplate.update(
				"INSERT INTO " +
					"`acl_role` (" +
					"`role`" +
				") VALUES (?)",
				new Object[] { role.getRoleId() }
			);
		} catch (Exception e) {
			throw new AclException("Could not create role: " + role.getRoleId());
		}

		// create membership roles
		if(parents != null) {
			for (RoleInterface parentRole : parents) {
				try {
					jdbcTemplate.update(
						"INSERT INTO `acl_role_parent` (" +
							"`role`, " +
							"`role_parent`" +
						") VALUES (?, ?)",
						new Object[]{
							role.getRoleId(),
							parentRole.getRoleId()
						}
					);
				} catch (Exception e) {
					throw new AclException("Could not create role parent: " + role.getRoleId());
				}
			}
		}
	}

	/**
	 * remove role
	 *
	 * @param role
	 * @return
	 */
	@Override
	public void removeRole(RoleInterface role) throws AclException {
		removeRole(role, false);
	}

	/**
	 * remove role and delete all acl entries
	 *
	 * @param role
	 * @param removeAcl
	 * @return
	 */
	@Override
	public void removeRole(RoleInterface role, boolean removeAcl) throws AclException {
		// check if role exists
		if(!hasRole(role))
			throw new AclException("Role doesnt exists: " + role.getRoleId());

		//remove existing acl entries
		if(removeAcl)
			clearByRole(role);

		// remove parent role
		try {
			jdbcTemplate.update(
				"DELETE FROM " +
					"`acl_role_parent` " +
				"WHERE " +
					"`role_parent` = ? OR " +
					"`role` = ?",
				new Object[] {
					role.getRoleId(),
					role.getRoleId()
				}
			);
		} catch (Exception e) {
			throw new AclException("Could not delete role parent: " + role.getRoleId());
		}

		// delete role
		try {
			jdbcTemplate.update(
				"DELETE FROM acl_role WHERE role = ?",
				new Object[] { role.getRoleId() }
			);
		} catch (Exception e) {
			throw new AclException("Could not delete role: " + role.getRoleId());
		}
	}

	/**
	 * get all roles
	 *
	 * @return
	 */
	@Override
	public List<BaseRole> getRoles() throws AclException {
		try {
			List<BaseRole> roles = jdbcTemplate.query(
				"SELECT * FROM acl_role ORDER BY id ASC",
				new RowMapper<BaseRole>() {
					public BaseRole mapRow(ResultSet rs, int rowNum)
						throws SQLException {
						BaseRole role = new BaseRole();
						role.setRoleId(rs.getString("role"));

						return role;
					}
				}
			);

			return roles;
		} catch (Exception e) {
			throw new AclException("Could not get roles: " + e.getMessage());
		}
	}

	/**
	 * check if resource exists
	 *
	 * @param resource
	 * @return
	 */
	@Override
	public boolean hasResource(ResourceInterface resource) {
		try {
			jdbcTemplate.queryForObject(
				"SELECT `id` FROM `acl_resource` WHERE `resource` = ?",
				Long.class, resource.getResourceId()
			);
		} catch(EmptyResultDataAccessException e) {
			return false;
		}

		return true;
	}

	/**
	 * add resource
	 *
	 * @param resource
	 */
	@Override
	public void addResource(ResourceInterface resource) throws AclException {
		// check if resource already exists
		if(hasResource(resource))
			throw new AclException("Resource already exists");

		// create resource entry in table acl_resource
		try {
			jdbcTemplate.update(
				"INSERT INTO `acl_resource` (`resource`) VALUES (?)",
				new Object[] { resource.getResourceId() }
			);
		} catch (Exception e) {
			throw new AclException("Could not create resource: " + resource.getResourceId());
		}
	}

	/**
	 * remove resource
	 *
	 * @param resource
	 */
	@Override
	public void removeResource(ResourceInterface resource) throws AclException {
		removeResource(resource, false);
	}

	/**
	 * remove resource and all acl entries
	 *
	 * @param resource
	 * @param removeAcl
	 */
	@Override
	public void removeResource(ResourceInterface resource, boolean removeAcl) throws AclException {
		if(!hasResource(resource))
			throw new AclException("Resource does not exist: " + resource.getResourceId());

		// remove existing acl entries
		if(removeAcl)
			clearByResource(resource);

		// delete role
		try {
			jdbcTemplate.update(
				"DELETE FROM `acl_resource` WHERE `resource` = ?",
				new Object[] { resource.getResourceId() }
			);
		} catch (Exception e) {
			throw new AclException("Could not delete resource: " + resource.getResourceId());
		}
	}

	/**
	 * get all resources
	 *
	 * @return
	 */
	@Override
	public List<BaseResource> getResources() throws AclException {
		try {
			List<BaseResource> resources = jdbcTemplate.query(
				"SELECT `resource` FROM `acl_resource` ORDER BY `id` ASC",
				new RowMapper<BaseResource>(){
					public BaseResource mapRow(ResultSet rs, int rowNum)
						throws SQLException {
						BaseResource resource = new BaseResource();
						resource.setResourceId(rs.getString("resource"));

						return resource;
					}
				}
			);

			return resources;
		} catch (Exception e) {
			throw new AclException("Could not get resources: " + e.getMessage());
		}
	}

	/**
	 * clear acl entries by role
	 *
	 * @param role
	 */
	@Override
	public void clearByRole(RoleInterface role) throws AclException {
		if(!hasRole(role))
			throw new AclException("Role does not exist: " + role.getRoleId());

		try {
			jdbcTemplate.update(
				"DELETE FROM " +
					"`acl` " +
				"WHERE " +
					"`role` = ?",
				new Object[] { role.getRoleId() }
			);
		} catch (Exception e) {
			throw new AclException("Could not delete acl entries for role: " + role.getRoleId());
		}
	}

	/**
	 * clear acl entries by resource
	 *
	 * @param resource
	 */
	@Override
	public void clearByResource(ResourceInterface resource) throws AclException {
		if(!hasResource(resource))
			throw new AclException("Resource does not exist: " + resource.getResourceId());

		try {
			jdbcTemplate.update(
				"DELETE FROM " +
					"`acl` " +
				"WHERE " +
					"`resource` = ?",
				new Object[]{ resource.getResourceId() }
			);
		} catch (Exception e) {
			throw new AclException("Could not delete acl entries for resource: " + resource.getResourceId());
		}
	}

	/**
	 * create allow acl entry
	 *
	 * @param role
	 * @param resource
	 * @param action
	 */
	@Override
	public void allow(RoleInterface role, ResourceInterface resource, String action) throws AclException {
		if(!hasRole(role))
			throw new AclException("Role does not exist: " + role.getRoleId());

		if(!hasResource(resource))
			throw new AclException("Resource does not exist: " + resource.getResourceId());

		try {
			jdbcTemplate.update(
				"INSERT IGNORE INTO " +
					"`acl` (" +
					"`role`, " +
					"`resource`, " +
					"`action`, " +
					"`right`" +
				") VALUES (" +
					"?, ?, ?, 'allow'" +
				")",
				new Object[] {
					role.getRoleId(),
					role.getRoleId(),
					resource.getResourceId(),
					action
				}
			);
		} catch (Exception e) {
			throw new AclException(
				"Could not create allow entry for role: " + role.getRoleId() +
					" resource: " + resource.getResourceId() + " action: " + action
			);
		}
	}

	/**
	 * create deny acl entry
	 *
	 * @param role
	 * @param resource
	 * @param action
	 */
	@Override
	public void deny(RoleInterface role, ResourceInterface resource, String action) throws AclException {
		if(!hasRole(role))
			throw new AclException("Role does not exist: " + role.getRoleId());

		if(!hasResource(resource))
			throw new AclException("Resource does not exist: " + resource.getResourceId());

		try {
			jdbcTemplate.update(
				"INSERT IGNORE INTO " +
					"`acl` (" +
					"`role`, " +
					"`resource`, " +
					"`action`, " +
					"`right`" +
				") VALUES (" +
					"?, ?, ?, 'deny'" +
				")",
				new Object[] {
					role.getRoleId(),
					role.getRoleId(),
					resource.getResourceId(),
					action
				}
			);
		} catch (Exception e) {
			throw new AclException(
				"Could not create deny entry for role: " + role.getRoleId() +
					" resource: " + resource.getResourceId() + " action: " + action
			);
		}
	}

	/**
	 * remove acl entry
	 *
	 * @param role
	 * @param resource
	 * @param action
	 */
	@Override
	public void remove(RoleInterface role, ResourceInterface resource, String action) throws AclException {
		if(!hasRole(role))
			throw new AclException("Role does not exist: " + role.getRoleId());

		if(!hasResource(resource))
			throw new AclException("Resource does not exist: " + resource.getResourceId());

		try {
			jdbcTemplate.update(
				"DELETE FROM " +
					"`acl` " +
				"WHERE " +
					"`role` = ? and " +
					"`resource` = ? and " +
					"`action` = ?",
				new Object[] {
					role.getRoleId(),
					role.getRoleId(),
					resource.getResourceId(),
					action
				}
			);
		} catch (Exception e) {
			throw new AclException(
				"Could not delete acl entry for role: " + role.getRoleId() +
					" resource: " + resource.getResourceId() + " action: " + action
			);
		}
	}

	/**
	 * check if acl entry exists
	 *
	 * @param role
	 * @param resource
	 * @param action
	 * @return
	 */
	@Override
	public boolean hasRule(RoleInterface role, ResourceInterface resource, String action) {
		try {
			jdbcTemplate.queryForObject(
				"SELECT " +
					"`id` " +
				"FROM " +
					"`acl` " +
				" WHERE " +
					"`role` = ? and " +
					"`resource` = ? and " +
					"`action` = ?",
				Long.class,
				new Object[] {
					role.getRoleId(),
					role.getRoleId(),
					resource.getResourceId(),
					action
				}
			);
		} catch(EmptyResultDataAccessException e) {
			return false;
		}

		return true;
	}

	/**
	 * check if role is allowed to access resource with given action
	 *
	 * @param role
	 * @param resource
	 * @param action
	 * @return
	 */
	@Override
	public boolean isAllowed(RoleInterface role, ResourceInterface resource, String action) throws AclException {
		return isAllowed(role, resource, action, false);
	}

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
	@Override
	public boolean isAllowed(
		RoleInterface role,
		ResourceInterface resource,
		String action,
		boolean strict
	) throws AclException {
		boolean allowed = false;

		if(!hasRole(role))
			throw new AclException("Role does not exist: " + role.getRoleId());

		if(!hasResource(resource))
			throw new AclException("Resource does not exist: " + resource.getResourceId());

		try {
			// check if we found acl for role directly
			// ein eintrag direkt an einer rolle hat die hoechste prioritaet
			String accessRight = (String) jdbcTemplate.queryForObject(
				"SELECT " +
					"`right` " +
				"FROM " +
					"`acl` " +
				"WHERE " +
					"`role`" + " = ? and " +
					"`resource`" + " = ? and " +
					"`action`" + " = ?",
				new Object[] {
					role.getRoleId(),
					resource.getResourceId(),
					action
				},
				String.class
			);

			// check if we got allow
			return accessRight.equals("allow");
		} catch (Exception e) {
			// nothing to do here
		}

		// check for strict
		if(strict)
			return allowed;

		// we did not found an direct acl entry for role - so lets check parents
		// ORDER BY id DESC - prio fuer den letzten eintrag ist damit am hoechsten
		List<String> roles =jdbcTemplate.queryForList(
			"SELECT " +
				"`role_parent` " +
			"FROM " +
				"`acl_role_parent` " +
			"WHERE " +
				"`role` = ? " +
			"ORDER BY id DESC",
			new Object[] { role.getRoleId() },
			String.class
		);

		for(String parentRole : roles) {
			// check acl for parent role
			if(isAllowed(new BaseRole(parentRole), resource, action))
				return true;
		}

		return allowed;
	}

	/**
	 * clear complete acl store with all roles, resources and entries
	 */
	@Override
	public void truncateAll() throws AclException {
		// delete role
		try {
			jdbcTemplate.update("TRUNCATE TABLE acl");
			jdbcTemplate.update("TRUNCATE TABLE acl_resource");
			jdbcTemplate.update("TRUNCATE TABLE acl_role");
			jdbcTemplate.update("TRUNCATE TABLE acl_role_parent");
		} catch (Exception e) {
			throw new AclException("Could not truncate acl database");
		}
	}
}
