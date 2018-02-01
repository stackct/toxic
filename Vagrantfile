# -*- mode: ruby -*-
# vi: set ft=ruby :
# -----------------------------------------------------------------------------
#  This file provisions a VM as a Toxic server using the VirtualBox 
#  provider and a Centos 7 image.
#
#  To decrypt with Ansible vault, the path to the password file needs to be 
#  supplied as an environment variable.
#     Example:  
#       $ VAULT_PASSWORD_FILE=/home/pswd.txt vagrant [up|provision]
# -----------------------------------------------------------------------------

Vagrant.configure(2) do |config|

  config.vm.define "intel" do |box| 
    box.vm.box = "centos/7"

    config.vm.provider "virtualbox" do |v|
      v.memory = 2048
    end

    box.vm.provision "ansible" do |ansible|
      ansible.vault_password_file = ENV['VAULT_PASSWORD_FILE'] if ENV['VAULT_PASSWORD_FILE']
      ansible.playbook = "ansible/site.yml"
    end
  end
end
