#!/usr/bin/env python
# coding=UTF-8
import json,sys
from collections import namedtuple
from ansible.parsing.dataloader import DataLoader
from ansible.vars import VariableManager
from ansible.inventory import Inventory
from ansible.playbook.play import Play
from ansible.executor.task_queue_manager import TaskQueueManager
from ansible.plugins.callback import CallbackBase
from ansible.executor.playbook_executor import PlaybookExecutor

##Ansible version 2.0+
class AnsibleResultCallback(CallbackBase):
    """A sample callback plugin used for performing an action as results come in

    If you want to collect all results into a single object for processing at
    the end of the execution, look into utilizing the ``json`` callback plugin
        or writing your own custom callback plugin
    """
    
    def v2_runner_on_ok(self, result, **kwargs):
        """Print a json representation of the result

        This method could store the result in an instance attribute for retrieval later
        """
        host = result._host
        self.results[host.name]=result._result
        #print json.dumps(self.results, indent=4)
    
    def v2_runner_on_unreachable(self, result, **kwargs):
        host = result._host
        self.results[host.name]=result._result
        #print json.dumps({host.name: result._result}, indent=4)
        
    def v2_runner_on_failed(self, result, **kwargs):  
        host = result._host
        self.results[host.name]=result._result
        #print json.dumps({host.name: result._result}, indent=4)   
        
    def initRunEnv(self):
        self.Options = namedtuple('Options', ['connection', 'module_path', 'forks', 'become','timeout',
                                              'become_method', 'become_user', 'check','listhosts',
                                              'listtasks','listtags','syntax'])
        ########## initialize needed objects ##########
        # 管理变量的类,包括主机,组,扩展等变量,之前版本是在 inventory 中的
        self.variable_manager = VariableManager()
        # 用来加载解析yaml文件或JSON内容,并且支持vault的解密
        self.loader = DataLoader()
        self.options = self.Options(connection='smart', module_path=None, forks=10, 
                       become=None,timeout=10, become_method=None, become_user=None, check=False,
                       listhosts=False,listtasks=False,listtags=False,syntax=False)
        #self.passwords = dict(vault_pass='secret')

        # Instantiate our ResultCallback for handling results as they come in
        self.results_callback = self

        # create inventory and pass to var manager
        self.inventory = Inventory(loader=self.loader, variable_manager=self.variable_manager)
        # 根据 inventory 加载对应变量
        self.variable_manager.set_inventory(self.inventory)
        # 增加外部变量
        #self.variable_manager.extra_vars={"ansible_ssh_user":"root" , "ansible_ssh_pass":"xxx"}
        self.results={}
                
    def runCmd(self, host_list, module_name, module_args,extra_args={}):
        self.initRunEnv()
        self.variable_manager.extra_vars = extra_args
        # create play with tasks
        #name:任务名,类似playbook中tasks中的name   
        #hosts: playbook中的hosts
        #tasks: playbook中的tasks, 其实这就是playbook的语法, 因为tasks的值是个列表,因此可以写入多个task
        play_source =  dict(
                            name = "Ansible Play",
                            hosts = host_list,
                            gather_facts = 'no',
                            tasks = [
                                     dict(action=dict(module=module_name, args=module_args), register='shell_out')
                                     #,dict(action=dict(module='debug', args=dict(msg='{{shell_out.stdout}}')))
                                     ]
                            )
        play = Play().load(play_source, variable_manager=self.variable_manager, loader=self.loader)
        
        # actually run it
        tqm = None
        try:
            tqm = TaskQueueManager(
                                   inventory=self.inventory,
                                   variable_manager=self.variable_manager,
                                   loader=self.loader,
                                   options=self.options,
                                   passwords=None,
                                   # Use our custom callback instead of the ``default`` callback plugin
                                   stdout_callback=self.results_callback,
                                   )
            tqm.run(play)
            #result = tqm.run(play)
            #print result
        finally:
            if tqm is not None:
                tqm.cleanup()
        print json.dumps(self.results, indent=4) 
        return json.dumps(self.results, indent=4)
        
    def runPlay(self,play_file,target,extra_args={}):
        self.initRunEnv()
        # 这里是一个列表, 因此可以运行多个playbook
        playbooks=[play_file]
        # 增加外部变量
        self.variable_manager.extra_vars = extra_args
        self.variable_manager.extra_vars = {"target":target }
        pb = PlaybookExecutor(playbooks=playbooks, inventory=self.inventory,
                              variable_manager=self.variable_manager, 
                              loader=self.loader, options=self.options, passwords=None)
        #pb._tqm._stdout_callback = self.results_callback  
        result = pb.run()
        return result

def AnsibleCmd(target,module,args,extra_args={}):
    return AnsibleResultCallback().runCmd(target,module,args,extra_args)
    
def AnsiblePlay(play_file,target,extra_args={}):
    return AnsibleResultCallback().runPlay(play_file,target,extra_args)
    
if __name__ == '__main__':
    #获取命令行参数
    AnsibleCmd(sys.argv[1],sys.argv[2],sys.argv[3]);
    #AnsibleResultCallback().runCmd('10.45.80.43','shell','ls -lrt')
    #AnsibleResultCallback().runPlay('play.yml','10.45.80.43')
    pass