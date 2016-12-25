package bgu.spl.a2.sim.tasks;

import bgu.spl.a2.Deferred;
import bgu.spl.a2.Task;
import bgu.spl.a2.sim.Product;
import bgu.spl.a2.sim.Warehouse;
import bgu.spl.a2.sim.conf.ManufactoringPlan;
import bgu.spl.a2.sim.tools.Tool;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by stephanieschustermann on 25/12/2016.
 */
public class ManufactureTask  extends Task<Product> {
    private String prodType;
    private Warehouse warehouse;
    private ArrayList<Tool> borrowedTools = new ArrayList<>();
    private AtomicLong startId;
    AtomicInteger whenResolveToolCounter  = new AtomicInteger(0);

    public ManufactureTask(String productType, Warehouse warehouse, long startId){
        this.prodType = productType;
        this.warehouse = warehouse;
        this.startId  = new AtomicLong(startId);
    }

    @Override
    protected void start() {
        // get the plan of the product
        ManufactoringPlan plan = warehouse.getPlan(this.prodType);

        Product result = new Product(this.startId.get(), this.prodType);

        if(plan.getParts() == null || plan.getParts().length == 0){
            complete(result);
        } else {
            ArrayList<Task<Product>> tasks = new ArrayList<>();

            // for each part spawn a manufacture task
            for (String part : plan.getParts()) {
                ManufactureTask task = new ManufactureTask(part, warehouse, startId.get());
                tasks.add(task);
                spawn(task);
                startId.incrementAndGet();
            }

            //Assembling part:
            // add the task to when resolve
            whenResolved(tasks, () -> {
                System.out.println("this is the callback from when resolve");
                // add each part to the product
                for(Task<Product> productTask: tasks){
                    result.addPart(productTask.getResult().get());
                }

                // for each tool get a when resolve callback
                for (String tool : plan.getTools()) {
                    Deferred<Tool> toolDeferred = warehouse.acquireTool(tool);
                    toolDeferred.whenResolved(() -> {
                        borrowedTools.add(toolDeferred.get());
                        int count = this.whenResolveToolCounter.addAndGet(1);

                        if (plan.getTools().length == count) {
                            long finalId = this.startId.get();
                            // TODO dont forget the start id and how to manage it to be unique
                            // use the useON function to get the id (sum them all)
                            for(Tool borrowed: this.borrowedTools){
                                for(Product part: result.getParts()){
                                    finalId = this.startId.addAndGet(borrowed.useOn(part));
                                }
                                // TODO how to set the final id?
                            }
                            // complete the task
                            complete(result);
                        }
                    });
                }
            });

        }
    }
}

