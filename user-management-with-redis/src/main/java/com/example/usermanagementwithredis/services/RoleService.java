package com.example.usermanagementwithredis.services;

import com.example.usermanagementwithredis.dtos.RoleResponse;
import com.example.usermanagementwithredis.entities.Role;
import com.example.usermanagementwithredis.repositories.RoleRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class RoleService {

    private final ModelMapper modelMapper;
    private final RoleRepository roleRepository;

    @Autowired
    public RoleService(ModelMapper modelMapper, RoleRepository roleRepository) {
        this.modelMapper = modelMapper;
        this.roleRepository = roleRepository;
    }

    public List<RoleResponse> getAll() {
        Iterable<Role> roles = roleRepository.findAll();
        return StreamSupport.stream(roles.spliterator(), false).map(r -> modelMapper.map(r, RoleResponse.class)).collect(Collectors.toList());
    }

}
