/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.test.user;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.UUID;

import org.apache.openmeetings.db.dao.user.GroupDao;
import org.apache.openmeetings.db.dao.user.GroupUserDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.user.Group;
import org.apache.openmeetings.db.entity.user.GroupUser;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.test.AbstractJUnitDefaults;
import org.apache.openmeetings.test.selenium.HeavyTests;
import org.apache.openmeetings.util.OmException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TestUserGroup extends AbstractJUnitDefaults {
	@Autowired
	private GroupUserDao groupUserDao;
	@Autowired
	private GroupDao groupDao;
	@Autowired
	private UserDao userDao;
	public static final String GROUP_NAME = "Test Group";

	private User getValidUser() {
		for (User u : userDao.getAllBackupUsers()) {
			if (!u.isDeleted() && u.getGroupUsers().size() > 0) {
				return u;
			}
		}
		fail("Unable to find valid user");
		return null; //unreachable
	}

	@Test
	public void getUsersByGroupId() {
		User u = getValidUser();
		Long groupId = u.getGroupUsers().get(0).getGroup().getId();
		List<GroupUser> ul = groupUserDao.get(groupId, 0, 9999);
		assertTrue("Default Group should contain at least 1 user: " + ul.size(), ul.size() > 0);
		
		GroupUser ou = groupUserDao.getByGroupAndUser(groupId, u.getId());
		assertNotNull("Unable to find [group, user] pair - [" + groupId + "," + u.getId() + "]", ou);
	}

	@Test
	public void addGroup() {
		Group g = new Group();
		g.setName(GROUP_NAME);
		Long groupId = groupDao.update(g, null).getId(); //inserted by not checked
		assertNotNull("New Group have valid id", groupId);
		
		List<GroupUser> ul = groupUserDao.get(groupId, 0, 9999);
		assertTrue("New Group should contain NO users: " + ul.size(), ul.size() == 0);
	}

	@Test
	public void addUserWithoutGroup() throws Exception {
		String uuid = UUID.randomUUID().toString();
		User u = getUser(uuid);
		u = userDao.update(u, null);
		assertNotNull("User successfully created", u.getId());
		checkEmptyGroup("dao.get()", userDao.get(u.getId()));
		try {
			checkEmptyGroup("dao.login()", userDao.login(u.getAddress().getEmail(), getRandomPass(uuid)));
			fail("User with no Group is unable to login");
		} catch (OmException e) {
			assertTrue("Expected Om Exception", "No Group assigned to user".equals(e.getMessage()));
		}
		checkEmptyGroup("dao.getByLogin(user)", userDao.getByLogin(u.getLogin(), u.getType(), u.getDomainId()));
	}


	@Test
	public void addLdapUserWithoutGroup() throws Exception {
		User u1 = getUser();
		u1.setType(User.Type.ldap);
		u1.setDomainId(1L);
		u1 = userDao.update(u1, null);
		checkEmptyGroup("dao.getByLogin(ldap)", userDao.getByLogin(u1.getLogin(), u1.getType(), u1.getDomainId()));
	}

	private void checkEmptyGroup(String prefix, User u) {
		assertNotNull(prefix + ":: Created user should be available", u);
		assertNotNull(prefix + ":: List<GroupUser> for newly created user should not be null", u.getGroupUsers());
		assertTrue(prefix + ":: List<GroupUser> for newly created user should be empty", u.getGroupUsers().isEmpty());
	}

	@Test
	@HeavyTests
	public void add10kUsers() throws Exception {
		List<Group> groups = groupDao.get(GROUP_NAME, 0, 1, null);
		Group g = null;
		if (groups == null || groups.isEmpty()) {
			g = new Group();
			g.setName(GROUP_NAME);
			g = groupDao.update(g, null);
		} else {
			g = groups.get(0);
		}
		for (int i = 0; i < 10000; ++i) {
			User u = createUser();
			u.getGroupUsers().add(new GroupUser(g, u));
			userDao.update(u, null);
		}
	}
}
