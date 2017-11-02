#!/bin/bash
curl http://127.0.0.1:7777/inventory/ansible/dynamic_inventory

#return the output format
#{
#    "databases": {
#        "hosts": ["host1.example.com", "host2.example.com"],
#        "vars": {
#            "a": true
#        }
#    },
#    "webservers": ["host2.example.com", "host3.example.com"],
#    "atlanta": {
#        "hosts": ["host1.example.com", "host4.example.com", "host5.example.com"],
#        "vars": {
#            "b": false
#        },
#        "children": ["marietta", "5points"]
#    },
#    "marietta": ["host6.example.com"],
#    "5points": ["host7.example.com"]
#}
##!/bin/sh
#echo '{"server":{"hosts":[10.45.80.26,10.45.80.27],
#		          "vars":{"ansible_ssh_user":"root","ansible_ssh_pass":"abc@123A"}},
#		"defalut":[10.45.80.1]}'
#-----------------------------------------------------------------------------------------
#echo n | ssh-keygen -t rsa -P "" -f ~/.ssh/id_rsa
#ansible 10.45.80.42 -m authorized_key 
#                        -a 'key={{ lookup("file", "~/.ssh/id_rsa.pub") }} state=present user=root'
#